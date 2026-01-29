// FILE: core/src/main/kotlin/core/Reducer.kt
package core

import core.invariants.verifyInvariants
import core.primitives.*
import core.rng.Rng
import core.state.*
import core.partial.PartialOutcomeResolver
import core.partial.PartialResolutionInput
import core.partial.TrophiesQuality

/**
 * Domain transition boundary.
 *
 * Why:
 * - This file is the only place where gameplay state is allowed to change.
 * - It enforces replay-grade determinism. The same inputs must yield the same outputs.
 * - It emits an auditable event journal so adapters can render, test, and replay without inference.
 *
 * How:
 * - Every mutation is expressed as `Command -> step(...) -> StepResult`.
 * - Randomness is consumed only through the provided [Rng]. Draw order is part of the contract.
 * - Invariants are verified after the transition. Violations are surfaced as events, not hidden.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────


private const val DEBUG_REJECTIONS = false

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Result of a single command application.
 *
 * Why:
 * - [state] is the single source of truth for the world after the command.
 * - [events] is a strict, causally ordered explanation of why that [state] exists.
 *
 * How:
 * - [events] is emitted in a stable order and is safe to persist as a journal.
 * - A consumer must never rebuild [state] from [events] unless it is implementing a verified replay.
 */
data class StepResult(
    /** Authoritative state after the transition. */
    val state: GameState,
    /** Audit trail for adapters, analytics, and replay. */
    val events: List<Event>
)

// ─────────────────────────────────────────────────────────────────────────────
// SeqContext (encapsulated event accumulator)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Event sequencing policy.
 *
 * Why:
 * - Seq numbers must be monotonic within a step so event order is non-negotiable.
 * - Handlers must not reorder, backdate, or "fix" events outside explicit policy.
 *
 * How:
 * - [emit] assigns the next seq and appends in causal order.
 * - [insertBeforeDayEnded] exists only to keep end-of-day summaries terminal.
 * - [renumberSeqFrom1] re-derives seq after insertion; seq is derived from final order.
 */
class SeqContext {
    private val events = ArrayList<Event>(32)
    private var nextSeq = 1L

    /**
     * Why:
     * - Prevents any handler from emitting an event without a stable order position.
     *
     * How:
     * - Ignores any incoming seq and overwrites it with the next monotonic value.
     */
    fun emit(event: Event): Event {
        val withSeq = assignSeq(event, nextSeq++)
        events.add(withSeq)
        return withSeq
    }

    /**
     * Why:
     * - Exposes events to callers without granting mutation rights.
     *
     * How:
     * - Returns a defensive copy in emission order.
     */
    fun snapshot(): List<Event> = events.toList()

    /**
     * Why:
     * - Keeps [DayEnded] as the terminal domain event for a day when extra diagnostics are added.
     *
     * How:
     * - Inserts before the last element only when that element is [DayEnded].
     */
    fun insertBeforeDayEnded(toInsert: List<Event>) {
        val lastEventIsDayEnded = events.lastOrNull() is DayEnded
        val insertIndex = if (lastEventIsDayEnded) events.size - 1 else events.size
        events.addAll(insertIndex, toInsert)
    }

    /**
     * Why:
     * - Seq is not identity. Seq is the audit order.
     *
     * How:
     * - Rewrites every event with seq=1..N using the final list order.
     */
    fun renumberSeqFrom1() {
        for (i in events.indices) {
            events[i] = assignSeq(events[i], (i + 1).toLong())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper functions
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Theft probability policy.
 *
 * Why:
 * - Salvage terms must influence agent incentives in a measurable way.
 * - The model must stay deterministic and debuggable.
 *
 * How:
 * - Produces a single integer percentage in a fixed range.
 * - All inputs are explicit. No hidden state is consulted.
 */
private fun computeTheftChance(hero: Hero, board: BoardContract): Int {
    return when {
        board.salvage == SalvagePolicy.GUILD && board.fee == 0 ->
            hero.traits.greed
        board.salvage == SalvagePolicy.GUILD && board.fee > 0 ->
            (hero.traits.greed - board.fee / 2).coerceAtLeast(0)
        board.salvage == SalvagePolicy.HERO -> 0
        board.salvage == SalvagePolicy.SPLIT ->
            ((hero.traits.greed - hero.traits.honesty) / 2).coerceAtLeast(0)
        else -> 0
    }
}

/**
 * Theft resolution.
 *
 * Why:
 * - Suspicion is a governance mechanic. It gates strict proof policy and drives player trust.
 *
 * How:
 * - Performs exactly one RNG roll.
 * - Returns the trophies that enter the return packet, plus a flag for suspicion.
 */
private fun resolveTheft(
    hero: Hero?,
    board: BoardContract?,
    baseTrophiesCount: Int,
    rng: Rng
): Pair<Int, Boolean> {
    if (hero == null || board == null || baseTrophiesCount <= 0) {
        return Pair(baseTrophiesCount, false)
    }

    val theftChance = computeTheftChance(hero, board)
    val theftRoll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)

    return if (theftRoll < theftChance) {
        val stolenAmount = (baseTrophiesCount + 1) / 2
        val reportedAmount = (baseTrophiesCount - stolenAmount).coerceAtLeast(0)
        Pair(reportedAmount, true)
    } else {
        Pair(baseTrophiesCount, false)
    }
}

/**
 * Outcome resolution.
 *
 * Why:
 * - The game needs repeatable risk so balance changes are testable.
 * - Outcome must depend only on hero capability, contract difficulty, and RNG.
 *
 * How:
 * - Uses one RNG roll and fixed probability buckets.
 * - Preserves draw count to keep replay compatibility.
 */
private fun resolveOutcome(hero: Hero?, contractDifficulty: Int, rng: Rng): Outcome {
    val heroPower = calculateHeroPower(hero)
    val rawSuccessChance = (heroPower - contractDifficulty + BalanceSettings.SUCCESS_FORMULA_OFFSET) *
        BalanceSettings.SUCCESS_FORMULA_MULTIPLIER

    // Clamp success chance to valid range, leaving room for FAIL_CHANCE_MIN
    val maxSuccessForFail = BalanceSettings.PERCENT_ROLL_MAX - BalanceSettings.PARTIAL_CHANCE_FIXED - BalanceSettings.FAIL_CHANCE_MIN
    val pSuccess = rawSuccessChance.coerceIn(BalanceSettings.SUCCESS_CHANCE_MIN, maxSuccessForFail)

    // Fixed partial chance
    val pPartial = BalanceSettings.PARTIAL_CHANCE_FIXED

    // Fail is the remainder, guaranteed >= FAIL_CHANCE_MIN by construction
    // pFail = 100 - pSuccess - pPartial >= FAIL_CHANCE_MIN

    val roll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
    return when {
        roll < pSuccess -> Outcome.SUCCESS
        roll < pSuccess + pPartial -> Outcome.PARTIAL
        // New DEATH tail: fixed high-roll death chance without extra RNG draws
        roll >= (BalanceSettings.PERCENT_ROLL_MAX - 5) -> Outcome.DEATH
        else -> Outcome.FAIL
    }
}

private fun assignSeq(event: Event, seq: Long): Event {
    return when (event) {
        is DayStarted -> event.copy(seq = seq)
        is InboxGenerated -> event.copy(seq = seq)
        is HeroesArrived -> event.copy(seq = seq)
        is ContractPosted -> event.copy(seq = seq)
        is ContractTaken -> event.copy(seq = seq)
        is WipAdvanced -> event.copy(seq = seq)
        is ContractResolved -> event.copy(seq = seq)
        is ReturnClosed -> event.copy(seq = seq)
        is TrophySold -> event.copy(seq = seq)
        is StabilityUpdated -> event.copy(seq = seq)
        is DayEnded -> event.copy(seq = seq)
        is CommandRejected -> event.copy(seq = seq)
        is InvariantViolated -> event.copy(seq = seq)
        is HeroDeclined -> event.copy(seq = seq)
        is TrophyTheftSuspected -> event.copy(seq = seq)
        is TaxDue -> event.copy(seq = seq)
        is TaxPaid -> event.copy(seq = seq)
        is TaxMissed -> event.copy(seq = seq)
        is GuildShutdown -> event.copy(seq = seq)
        is GuildRankUp -> event.copy(seq = seq)
        is ProofPolicyChanged -> event.copy(seq = seq)
        is ContractDraftCreated -> event.copy(seq = seq)
        is ContractTermsUpdated -> event.copy(seq = seq)
        is ContractCancelled -> event.copy(seq = seq)
        is ContractAutoResolved -> event.copy(seq = seq)
        is HeroDied -> event.copy(seq = seq)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main reducer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Deterministic state transition.
 *
 * Why:
 * - This is the single entry point for gameplay mutation.
 * - It is the enforcement point for validation, auditability, and invariant visibility.
 *
 * How:
 * - First validates [cmd]. On rejection: returns the unchanged [state] and one [CommandRejected] event.
 * - On acceptance: increments revision exactly once, then routes to a command handler.
 * - After the handler: verifies invariants and emits [InvariantViolated] events without rollback.
 * - Final event order is strict; seq is derived from that order.
 */
fun step(state: GameState, cmd: Command, rng: Rng): StepResult {
    val validationResult = canApply(state, cmd)
    if (validationResult is ValidationResult.Rejected) {
        var detailText = validationResult.detail
        if (validationResult.reason == RejectReason.INVALID_STATE) {
            if (!detailText.contains("money", ignoreCase = true) && !detailText.contains("escrow", ignoreCase = true)) {
                detailText = "$detailText (money/escrow)"
            }
        }

        if (DEBUG_REJECTIONS) println("[REJ-DETAIL] $detailText")

        // Use unified assignSeq for CommandRejected
        val event = assignSeq(
            CommandRejected(
                day = state.meta.dayIndex,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                cmdType = cmd::class.simpleName ?: "Unknown",
                reason = validationResult.reason,
                detail = detailText
            ),
            1L
        )
        return StepResult(state, listOf(event))
    }

    val stateWithRevision = state.copy(meta = state.meta.copy(revision = state.meta.revision + 1))
    val seqCtx = SeqContext()

    val newState = when (cmd) {
        is AdvanceDay -> handleAdvanceDay(stateWithRevision, cmd, rng, seqCtx)
        is PostContract -> handlePostContract(stateWithRevision, cmd, rng, seqCtx)
        is CloseReturn -> handleCloseReturn(stateWithRevision, cmd, rng, seqCtx)
        is SellTrophies -> handleSellTrophies(stateWithRevision, cmd, rng, seqCtx)
        is PayTax -> handlePayTax(stateWithRevision, cmd, rng, seqCtx)
        is SetProofPolicy -> handleSetProofPolicy(stateWithRevision, cmd, rng, seqCtx)
        is CreateContract -> handleCreateContract(stateWithRevision, cmd, rng, seqCtx)
        is UpdateContractTerms -> handleUpdateContractTerms(stateWithRevision, cmd, rng, seqCtx)
        is CancelContract -> handleCancelContract(stateWithRevision, cmd, rng, seqCtx)
    }

    val violations = verifyInvariants(newState)
    if (violations.isNotEmpty()) {
        val violationEvents = violations.map { v ->
            InvariantViolated(
                day = newState.meta.dayIndex,
                revision = newState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                invariantId = v.invariantId,
                details = v.details
            )
        }
        seqCtx.insertBeforeDayEnded(violationEvents)
        seqCtx.renumberSeqFrom1()
    }

    return StepResult(newState, seqCtx.snapshot())
}

// ─────────────────────────────────────────────────────────────────────────────
// AdvanceDay phases
// ─────────────────────────────────────────────────────────────────────────────

/**
 * AdvanceDay is a domain transaction.
 *
 * Why:
 * - A day tick must be atomic from the player perspective.
 * - The pipeline must be stable so telemetry and replay stay comparable over time.
 *
 * How:
 * - Runs a fixed phase order.
 * - Emits summary events as the authoritative narrative of the day.
 */
@Suppress("UNUSED_PARAMETER")
private fun handleAdvanceDay(
    state: GameState,
    cmd: AdvanceDay,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val newDay = state.meta.dayIndex + 1
    var workingState = state.copy(
        meta = state.meta.copy(dayIndex = newDay),
        heroes = state.heroes.copy(arrivalsToday = emptyList())
    )

    ctx.emit(DayStarted(day = newDay, revision = workingState.meta.revision, cmdId = cmd.cmdId, seq = 0L))

    val rankOrdinal = workingState.guild.guildRank.coerceIn(1, RANK_THRESHOLDS.size)
    val rankThreshold = RANK_THRESHOLDS.firstOrNull { it.rankOrdinal == rankOrdinal }
    val nInbox = rankThreshold?.inboxMultiplier?.times(2) ?: BalanceSettings.DEFAULT_N_INBOX
    val nHeroes = rankThreshold?.heroesMultiplier?.times(2) ?: BalanceSettings.DEFAULT_N_HEROES

    workingState = phaseInboxGeneration(workingState, cmd, rng, ctx, newDay, nInbox)
    workingState = phaseHeroArrivals(workingState, cmd, rng, ctx, newDay, nHeroes)
    workingState = phaseAutoResolveInbox(workingState, cmd, rng, ctx, newDay)
    workingState = phasePickup(workingState, cmd, rng, ctx, newDay)

    val (stateAfterWip, successfulReturns, failedReturns) = phaseWipAndResolve(workingState, cmd, rng, ctx, newDay)
    workingState = stateAfterWip

    workingState = phaseStabilityUpdate(workingState, cmd, ctx, newDay, successfulReturns, failedReturns)
    workingState = phaseTax(workingState, cmd, ctx, newDay)
    workingState = phaseDayEndedAndSnapshot(workingState, cmd, ctx, newDay)

    return workingState
}

/**
 * Why:
 * - Inbox exists to decouple content generation from player publication decisions.
 *
 * How:
 * - Generates drafts using stability-driven threat scaling and seeded RNG.
 * - Advances id counters in one direction only.
 */
private fun phaseInboxGeneration(
    state: GameState,
    cmd: AdvanceDay,
    rng: Rng,
    ctx: SeqContext,
    newDay: Int,
    nInbox: Int
): GameState {
    val newDrafts = ArrayList<ContractDraft>(nInbox)
    val contractIds = IntArray(nInbox)
    var nextContractId = state.meta.ids.nextContractId

    for (i in 0 until nInbox) {
        val draftId = nextContractId++
        contractIds[i] = draftId

        val threatLevel = calculateThreatLevel(state.region.stability)
        val baseDifficulty = calculateBaseDifficulty(threatLevel, rng)

        newDrafts.add(
            ContractDraft(
                id = ContractId(draftId),
                createdDay = newDay,
                nextAutoResolveDay = newDay + BalanceSettings.AUTO_RESOLVE_INTERVAL_DAYS,
                title = "Request #$draftId",
                rankSuggested = Rank.F,
                feeOffered = 0,
                salvage = SalvagePolicy.GUILD,
                baseDifficulty = baseDifficulty,
                proofHint = "proof"
            )
        )
    }

    ctx.emit(
        InboxGenerated(
            day = newDay,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            count = nInbox,
            contractIds = contractIds
        )
    )

    return state.copy(
        contracts = state.contracts.copy(inbox = state.contracts.inbox + newDrafts),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextContractId = nextContractId))
    )
}

/**
 * Why:
 * - Heroes are agents. Arrivals are the simulation's supply side.
 *
 * How:
 * - Appends new heroes and records their ids as "arrivalsToday" to lock ordering.
 */
private fun phaseHeroArrivals(
    state: GameState,
    cmd: AdvanceDay,
    @Suppress("UNUSED_PARAMETER") rng: Rng,
    ctx: SeqContext,
    newDay: Int,
    nHeroes: Int
): GameState {
    val newHeroes = ArrayList<Hero>(nHeroes)
    val heroIdsRaw = IntArray(nHeroes)
    var nextHeroId = state.meta.ids.nextHeroId

    for (i in 0 until nHeroes) {
        val heroId = nextHeroId++
        heroIdsRaw[i] = heroId
        val heroName = core.flavour.HeroNames.POOL[rng.nextInt(core.flavour.HeroNames.POOL.size)]
        newHeroes.add(
            Hero(
                id = HeroId(heroId),
                name = heroName,
                rank = Rank.F,
                klass = HeroClass.WARRIOR,
                traits = Traits(greed = 50, honesty = 50, courage = 50),
                status = HeroStatus.AVAILABLE,
                historyCompleted = 0
            )
        )
    }

    val arrivalsToday = List(nHeroes) { HeroId(heroIdsRaw[it]) }

    ctx.emit(
        HeroesArrived(
            day = newDay,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            count = nHeroes,
            heroIds = heroIdsRaw
        )
    )

    return state.copy(
        heroes = state.heroes.copy(
            roster = state.heroes.roster + newHeroes,
            arrivalsToday = arrivalsToday
        ),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextHeroId = nextHeroId))
    )
}

/**
 * Why:
 * - The world must not accumulate stale drafts forever.
 * - Ignored requests must have a measurable systemic cost.
 *
 * How:
 * - Applies a deterministic bucket decision per due draft.
 * - Encodes the systemic impact via explicit events and stability adjustment.
 */
private fun phaseAutoResolveInbox(
    state: GameState,
    cmd: AdvanceDay,
    rng: Rng,
    ctx: SeqContext,
    newDay: Int
): GameState {
    val dueDrafts = state.contracts.inbox.filter { it.nextAutoResolveDay <= newDay }

    if (dueDrafts.isEmpty()) return state

    val updatedInbox = state.contracts.inbox.toMutableList()
    var cumulativeStabilityDelta = 0

    for (draft in dueDrafts) {
        // Removed unreachable else branch
        val bucket = when (rng.nextInt(3)) {
            0 -> AutoResolveBucket.GOOD
            1 -> AutoResolveBucket.NEUTRAL
            else -> AutoResolveBucket.BAD
        }

        ctx.emit(
            ContractAutoResolved(
                day = newDay,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                draftId = draft.id.value,
                bucket = bucket
            )
        )

        when (bucket) {
            AutoResolveBucket.GOOD -> {
                updatedInbox.removeIf { it.id == draft.id }
            }
            AutoResolveBucket.NEUTRAL -> {
                val idx = updatedInbox.indexOfFirst { it.id == draft.id }
                if (idx >= 0) {
                    updatedInbox[idx] = draft.copy(nextAutoResolveDay = newDay + BalanceSettings.AUTO_RESOLVE_INTERVAL_DAYS)
                }
            }
            AutoResolveBucket.BAD -> {
                updatedInbox.removeIf { it.id == draft.id }
                cumulativeStabilityDelta -= BalanceSettings.STABILITY_PENALTY_BAD_AUTO_RESOLVE
            }
        }
    }

    var workingState = state.copy(
        contracts = state.contracts.copy(inbox = updatedInbox)
    )

    if (cumulativeStabilityDelta != 0) {
        val oldStability = workingState.region.stability
        val newStability = (oldStability + cumulativeStabilityDelta).coerceIn(0, 100)
        workingState = workingState.copy(region = workingState.region.copy(stability = newStability))

        ctx.emit(
            StabilityUpdated(
                day = newDay,
                revision = workingState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                oldStability = oldStability,
                newStability = newStability
            )
        )
    }

    return workingState
}

/**
 * Why:
 * - Contract pickup is the core agent decision loop.
 * - The player does not micro-manage; the system must select and explain.
 *
 * How:
 * - Iterates arriving heroes in deterministic id order.
 * - Locks the chosen board contract before creating the active contract.
 */
private fun phasePickup(
    state: GameState,
    cmd: AdvanceDay,
    @Suppress("UNUSED_PARAMETER") rng: Rng,
    ctx: SeqContext,
    newDay: Int
): GameState {
    val arrivingHeroIds = state.heroes.arrivalsToday.sortedBy { it.value }
    var nextActiveContractId = state.meta.ids.nextActiveContractId

    val board = state.contracts.board.toMutableList()
    val roster = state.heroes.roster.toMutableList()
    val newActives = ArrayList<ActiveContract>(arrivingHeroIds.size)

    val rosterIndexById = HashMap<Int, Int>(roster.size * 2)
    for (i in roster.indices) rosterIndexById[roster[i].id.value] = i

    val openIdx = IntArray(board.size)
    var openCount = 0
    for (i in board.indices) if (board[i].status == BoardStatus.OPEN) openIdx[openCount++] = i

    for (heroId in arrivingHeroIds) {
        val heroIndex = rosterIndexById[heroId.value] ?: continue
        val hero = roster[heroIndex]

        var bestBoardIndex = -1
        var bestScore = Int.MIN_VALUE
        var bestBoard: BoardContract? = null

        for (k in 0 until openCount) {
            val bi = openIdx[k]
            val b = board[bi]
            if (b.status != BoardStatus.OPEN) continue
            val score = evaluateContractForHero(hero, b, b.baseDifficulty).score
            if (score > bestScore) {
                bestScore = score
                bestBoardIndex = bi
                bestBoard = b
            }
        }

        if (bestBoardIndex < 0) continue
        val chosen = bestBoard!!

        if (bestScore < 0) {
            ctx.emit(
                HeroDeclined(
                    day = newDay,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    heroId = heroId.value,
                    boardContractId = chosen.id.value,
                    reason = if (bestScore < BalanceSettings.DECLINE_HARD_THRESHOLD) "unprofitable" else "too_risky"
                )
            )
            continue
        }

        val activeId = nextActiveContractId++
        newActives.add(
            ActiveContract(
                id = ActiveContractId(activeId),
                boardContractId = chosen.id,
                takenDay = newDay,
                daysRemaining = BalanceSettings.DAYS_REMAINING_INIT,
                heroIds = listOf(heroId),
                status = ActiveStatus.WIP
            )
        )

        board[bestBoardIndex] = chosen.copy(status = BoardStatus.LOCKED)
        roster[heroIndex] = hero.copy(status = HeroStatus.ON_MISSION)

        ctx.emit(
            ContractTaken(
                day = newDay,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                activeContractId = activeId,
                boardContractId = chosen.id.value,
                heroIds = intArrayOf(heroId.value),
                daysRemaining = BalanceSettings.DAYS_REMAINING_INIT
            )
        )
    }

    return state.copy(
        contracts = state.contracts.copy(board = board, active = state.contracts.active + newActives),
        heroes = state.heroes.copy(roster = roster),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextActiveContractId = nextActiveContractId))
    )
}

/**
 * Why:
 * - WIP advancement and resolution must be serial and stable to keep replay valid.
 * - Auto-closure must apply economy, rank, and hero lifecycle rules without adapter logic.
 *
 * How:
 * - Processes actives in deterministic id order.
 * - Emits intermediate and terminal events in causal order.
 */
private data class WipResolveResult(
    val state: GameState,
    val successfulReturns: Int,
    val failedReturns: Int
)

private fun phaseWipAndResolve(
    state: GameState,
    cmd: AdvanceDay,
    rng: Rng,
    ctx: SeqContext,
    newDay: Int
): WipResolveResult {
    var workingState = state
    var successfulReturns = 0
    var failedReturns = 0

    val sortedActives = workingState.contracts.active.sortedBy { it.id.value }
    val updatedActives = mutableListOf<ActiveContract>()
    val newReturns = mutableListOf<ReturnPacket>()

    // Pre-build lookup maps for O(1) access
    val boardById = workingState.contracts.board.associateBy { it.id }
    val heroById = workingState.heroes.roster.associateBy { it.id }

    for (active in sortedActives) {
        if (active.status != ActiveStatus.WIP) {
            updatedActives.add(active)
            continue
        }

        val newDaysRemaining = active.daysRemaining - 1

        ctx.emit(
            WipAdvanced(
                day = newDay,
                revision = workingState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                activeContractId = active.id.value,
                daysRemaining = newDaysRemaining
            )
        )

        if (newDaysRemaining == 0) {
            // Cache lookups once for this active contract
            val heroId = active.heroIds.firstOrNull()
            val hero = heroId?.let { heroById[it] }
            val boardContract = boardById[active.boardContractId]
            val contractDifficulty = boardContract?.baseDifficulty ?: 1

            val outcome = resolveOutcome(hero, contractDifficulty, rng)
            val requiresPlayerClose = (outcome == Outcome.PARTIAL)
            val baseTrophiesCount = when (outcome) {
                Outcome.SUCCESS -> 1 + rng.nextInt(3)
                Outcome.PARTIAL -> 1
                else -> 0
            }

            // Direct index access instead of toTypedArray()
            val trophiesQuality = Quality.entries[rng.nextInt(Quality.entries.size)]

            // Unified theft resolution using cached hero/board
            val (trophiesCount, theftOccurred) = resolveTheft(hero, boardContract, baseTrophiesCount, rng)

            if (theftOccurred && heroId != null) {
                ctx.emit(
                    TrophyTheftSuspected(
                        day = newDay,
                        revision = workingState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        activeContractId = active.id.value,
                        heroId = heroId.value,
                        expectedTrophies = baseTrophiesCount,
                        reportedTrophies = trophiesCount
                    )
                )
            }

            newReturns.add(
                ReturnPacket(
                    activeContractId = active.id,
                    boardContractId = active.boardContractId,
                    heroIds = active.heroIds,
                    resolvedDay = newDay,
                    outcome = outcome,
                    trophiesCount = trophiesCount,
                    trophiesQuality = trophiesQuality,
                    reasonTags = emptyList(),
                    requiresPlayerClose = requiresPlayerClose,
                    suspectedTheft = theftOccurred
                )
            )

            if (!requiresPlayerClose) {
                if (outcome == Outcome.SUCCESS) successfulReturns++
                else if (outcome == Outcome.FAIL) failedReturns++
            }

            ctx.emit(
                ContractResolved(
                    day = newDay,
                    revision = workingState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    activeContractId = active.id.value,
                    outcome = outcome,
                    trophiesCount = trophiesCount,
                    quality = trophiesQuality,
                    reasonTags = intArrayOf()
                )
            )

            if (requiresPlayerClose) {
                updatedActives.add(active.copy(daysRemaining = 0, status = ActiveStatus.RETURN_READY))
            } else {
                // Auto-close path (requiresPlayerClose == false): settle immediately.

                // Salvage -> trophies stock
                val trophiesGuildGets = if (boardContract != null) {
                    when (boardContract.salvage) {
                        SalvagePolicy.GUILD -> trophiesCount
                        SalvagePolicy.HERO -> 0
                        SalvagePolicy.SPLIT -> trophiesCount / 2
                    }
                } else 0
                val newTrophiesStock = workingState.economy.trophiesStock + trophiesGuildGets

                // Player only paid/reserved the portion not covered by client deposit
                val fee = boardContract?.fee ?: 0
                val clientDeposit = boardContract?.clientDeposit ?: 0

                // New accounting model: reserved holds clientDeposit only
                val newReservedCopper = workingState.economy.reservedCopper - clientDeposit
                val newMoneyCopper =
                    if (outcome == Outcome.FAIL || outcome == Outcome.DEATH) workingState.economy.moneyCopper
                    else workingState.economy.moneyCopper - fee

                val heroIdSet = active.heroIds.map { it.value }.toSet()

                // Handle hero removal on DEATH: remove from roster and emit HeroDied events
                val (updatedRoster, updatedArrivals, heroDiedEvents) = if (outcome == Outcome.DEATH) {
                    val remaining = workingState.heroes.roster.filter { h -> !heroIdSet.contains(h.id.value) }
                    val remainingArrivals = workingState.heroes.arrivalsToday.filter { id -> !heroIdSet.contains(id.value) }
                    val died = heroIdSet.map { hid ->
                        HeroDied(
                            day = workingState.meta.dayIndex,
                            revision = workingState.meta.revision,
                            cmdId = cmd.cmdId,
                            seq = 0L,
                            heroId = hid,
                            activeContractId = active.id.value,
                            boardContractId = (boardContract?.id?.value ?: active.boardContractId.value)
                        )
                    }
                    Triple(remaining, remainingArrivals, died)
                } else {
                    val updated = workingState.heroes.roster.map { h ->
                        if (heroIdSet.contains(h.id.value)) {
                            h.copy(status = HeroStatus.AVAILABLE, historyCompleted = h.historyCompleted + 1)
                        } else h
                    }
                    Triple(updated, workingState.heroes.arrivalsToday, emptyList<Event>())
                }

                val updatedBoard = if (boardContract != null && boardContract.status == BoardStatus.LOCKED) {
                    val hasNonClosedActives = updatedActives.any { a ->
                        a.boardContractId == boardContract.id && a.status != ActiveStatus.CLOSED
                    }
                    if (!hasNonClosedActives) {
                        workingState.contracts.board.map { b ->
                            if (b.id == boardContract.id) b.copy(status = BoardStatus.COMPLETED) else b
                        }
                    } else workingState.contracts.board
                } else workingState.contracts.board

                val newCompleted = workingState.guild.completedContractsTotal + 1
                val (newRank, contractsForNext) = calculateNextRank(newCompleted, workingState.guild.guildRank)

                var newGuildState = workingState.guild.copy(
                    completedContractsTotal = newCompleted,
                    contractsForNextRank = contractsForNext
                )

                if (newRank != workingState.guild.guildRank) {
                    ctx.emit(
                        GuildRankUp(
                            day = workingState.meta.dayIndex,
                            revision = workingState.meta.revision,
                            cmdId = cmd.cmdId,
                            seq = 0L,
                            oldRank = workingState.guild.guildRank,
                            newRank = newRank,
                            completedContracts = newCompleted
                        )
                    )
                    newGuildState = newGuildState.copy(guildRank = newRank)
                }

                workingState = workingState.copy(
                    contracts = workingState.contracts.copy(board = updatedBoard),
                    heroes = workingState.heroes.copy(roster = updatedRoster, arrivalsToday = updatedArrivals),
                    economy = workingState.economy.copy(
                        trophiesStock = newTrophiesStock,
                        reservedCopper = newReservedCopper,
                        moneyCopper = newMoneyCopper
                    ),
                    guild = newGuildState
                )

                // Emit HeroDied events if any
                for (e in heroDiedEvents) ctx.emit(e)

                ctx.emit(
                    ReturnClosed(
                        day = workingState.meta.dayIndex,
                        revision = workingState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        activeContractId = active.id.value
                    )
                )
            }
        } else {
            updatedActives.add(active.copy(daysRemaining = newDaysRemaining))
        }
    }

    workingState = workingState.copy(
        contracts = workingState.contracts.copy(
            active = updatedActives,
            returns = workingState.contracts.returns + newReturns
        )
    )

    return WipResolveResult(workingState, successfulReturns, failedReturns)
}

/**
 * Why:
 * - Stability is the public health meter of the region.
 * - It is the long-term difficulty driver.
 *
 * How:
 * - Applies a single clamp-bounded delta and emits a single event when changed.
 */
private fun phaseStabilityUpdate(
    state: GameState,
    cmd: AdvanceDay,
    ctx: SeqContext,
    newDay: Int,
    successfulReturns: Int,
    failedReturns: Int
): GameState {
    val oldStability = state.region.stability
    val delta = successfulReturns - failedReturns
    val newStability = (oldStability + delta).coerceIn(0, 100)

    if (newStability == oldStability) return state

    ctx.emit(
        StabilityUpdated(
            day = newDay,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            oldStability = oldStability,
            newStability = newStability
        )
    )

    return state.copy(region = state.region.copy(stability = newStability))
}

/**
 * Why:
 * - DayEnded is a stable integration point for analytics and UI.
 *
 * How:
 * - Captures a snapshot from the authoritative state without extra computation paths.
 */
private fun phaseDayEndedAndSnapshot(
    state: GameState,
    cmd: AdvanceDay,
    ctx: SeqContext,
    newDay: Int
): GameState {
    val snapshot = DaySnapshot(
        day = newDay,
        revision = state.meta.revision,
        money = state.economy.moneyCopper,
        trophies = state.economy.trophiesStock,
        regionStability = state.region.stability,
        guildReputation = state.guild.reputation,
        inboxCount = state.contracts.inbox.size,
        boardCount = state.contracts.board.size,
        activeCount = state.contracts.active.count { it.status == ActiveStatus.WIP },
        returnsNeedingCloseCount = state.contracts.returns.count { it.requiresPlayerClose }
    )

    ctx.emit(
        DayEnded(
            day = newDay,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            snapshot = snapshot
        )
    )

    return state
}

/**
 * Why:
 * - Taxes are the primary growth limiter.
 * - Misses must accumulate into an unambiguous shutdown risk.
 *
 * How:
 * - Evaluates due-day after the day pipeline.
 * - Applies penalty and miss counter updates in one place.
 */
private fun phaseTax(
    state: GameState,
    cmd: AdvanceDay,
    ctx: SeqContext,
    newDay: Int
): GameState {
    if (newDay < state.meta.taxDueDay) return state

    val totalDue = state.meta.taxAmountDue + state.meta.taxPenalty
    if (totalDue > 0) {
        val penaltyAdded = (totalDue * BalanceSettings.TAX_PENALTY_PERCENT) / 100
        val newTaxPenalty = state.meta.taxPenalty + penaltyAdded
        val newMissed = state.meta.taxMissedCount + 1

        val afterMissedState = state.copy(
            meta = state.meta.copy(
                taxPenalty = newTaxPenalty,
                taxMissedCount = newMissed,
                taxDueDay = state.meta.taxDueDay + BalanceSettings.TAX_INTERVAL_DAYS,
                taxAmountDue = calculateTaxAmount(state.guild.guildRank, BalanceSettings.TAX_BASE_AMOUNT)
            )
        )

        ctx.emit(
            TaxMissed(
                day = newDay,
                revision = afterMissedState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                amountDue = totalDue,
                penaltyAdded = penaltyAdded,
                missedCount = newMissed
            )
        )

        if (newMissed >= BalanceSettings.TAX_MAX_MISSED) {
            ctx.emit(
                GuildShutdown(
                    day = newDay,
                    revision = afterMissedState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    reason = "tax_evasion"
                )
            )
        }

        return afterMissedState
    } else {
        val newState = state.copy(
            meta = state.meta.copy(
                taxDueDay = state.meta.taxDueDay + BalanceSettings.TAX_INTERVAL_DAYS,
                taxAmountDue = calculateTaxAmount(state.guild.guildRank, BalanceSettings.TAX_BASE_AMOUNT)
            )
        )

        ctx.emit(
            TaxDue(
                day = newDay,
                revision = newState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                amountDue = newState.meta.taxAmountDue,
                dueDay = newState.meta.taxDueDay
            )
        )

        return newState
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Other command handlers
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Why:
 * - Trophy liquidation is the main short-term liquidity tool.
 *
 * How:
 * - Converts trophies into copper using a fixed exchange rate.
 * - Emits a single event that fully explains the transaction.
 */
@Suppress("UNUSED_PARAMETER", "ReturnCount")
private fun handleSellTrophies(
    state: GameState,
    cmd: SellTrophies,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val total = state.economy.trophiesStock
    if (total <= 0) return state

    val amountToSell = if (cmd.amount <= 0) total else minOf(cmd.amount, total)
    if (amountToSell <= 0) return state

    val moneyGained = amountToSell

    val newState = state.copy(
        economy = state.economy.copy(
            trophiesStock = total - amountToSell,
            moneyCopper = state.economy.moneyCopper + moneyGained
        )
    )

    ctx.emit(
        TrophySold(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            amount = amountToSell,
            moneyGained = moneyGained
        )
    )

    return newState
}

/**
 * Why:
 * - Publication is the player's commitment point.
 * - Escrow exists to make commitment economically binding.
 *
 * How:
 * - Moves a draft from inbox to board.
 * - Reserves only the player's top-up portion; client deposit stays external to player funds.
 */
@Suppress("UNUSED_PARAMETER")
private fun handlePostContract(
    state: GameState,
    cmd: PostContract,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val draft = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.inboxId } ?: return state
    val fee = cmd.fee

    val boardContract = BoardContract(
        id = draft.id,
        postedDay = state.meta.dayIndex,
        title = draft.title,
        rank = draft.rankSuggested,
        fee = fee,
        salvage = cmd.salvage,
        baseDifficulty = draft.baseDifficulty,
        status = BoardStatus.OPEN,
        clientDeposit = draft.clientDeposit
    )

    val newState = state.copy(
        contracts = state.contracts.copy(
            inbox = state.contracts.inbox.filter { it.id.value.toLong() != cmd.inboxId },
            board = state.contracts.board + boardContract
        ),
        // Escrow semantics:
        // - money += clientDeposit: guild receives the client's deposit
        // - reserved += clientDeposit: deposit is locked until completion
        // - available stays unchanged (both increase by deposit)
        economy = state.economy.copy(
            moneyCopper = state.economy.moneyCopper + draft.clientDeposit,
            reservedCopper = state.economy.reservedCopper + draft.clientDeposit
        )
    )

    ctx.emit(
        ContractPosted(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            boardContractId = boardContract.id.value,
            fromInboxId = draft.id.value,
            rank = draft.rankSuggested,
            fee = fee,
            salvage = cmd.salvage,
            clientDeposit = draft.clientDeposit
        )
    )

    return newState
}

/**
 * Why:
 * - Manual return closure is the player's governance moment.
 * - Strict proof is enforced by refusal to mutate state, not by rejection spam.
 *
 * How:
 * - In STRICT: silently no-ops on damaged proof or suspected theft.
 * - Otherwise: applies salvage, closes the active, releases escrow, and advances rank.
 */
@Suppress("UNUSED_PARAMETER", "ReturnCount")
private fun handleCloseReturn(
    state: GameState,
    cmd: CloseReturn,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val ret = state.contracts.returns.firstOrNull { it.activeContractId.value.toLong() == cmd.activeContractId } ?: return state
    val board = state.contracts.board.firstOrNull { it.id == ret.boardContractId }

    val policy = state.guild.proofPolicy
    if (policy == ProofPolicy.STRICT) {
        // Fixed: use enum comparison instead of string comparison
        if (ret.trophiesQuality == Quality.DAMAGED || ret.suspectedTheft) return state
    }

    val heroIdSet = ret.heroIds.map { it.value }.toSet()
    val updatedRoster = state.heroes.roster.map { hero ->
        if (heroIdSet.contains(hero.id.value)) hero.copy(status = HeroStatus.AVAILABLE, historyCompleted = hero.historyCompleted + 1) else hero
    }

    val updatedReturns = state.contracts.returns.filter { it.activeContractId.value.toLong() != cmd.activeContractId }
    val updatedActives = state.contracts.active.map { active ->
        if (active.id.value.toLong() == cmd.activeContractId) active.copy(status = ActiveStatus.CLOSED) else active
    }

    var newTrophiesStock = state.economy.trophiesStock
    if (board != null) {
        val trophiesGuildGets = when (board.salvage) {
            SalvagePolicy.GUILD -> ret.trophiesCount
            SalvagePolicy.HERO -> 0
            SalvagePolicy.SPLIT -> ret.trophiesCount / 2
        }
        newTrophiesStock += trophiesGuildGets
    }

    // Player only paid/reserved the portion not covered by client deposit
    val fee = board?.fee ?: 0
    val clientDeposit = board?.clientDeposit ?: 0

    // PARTIAL policy: apply deterministic normalization via resolver (PoC: floor( normal / 2 )).
    val (newReservedCopper, newMoneyCopper) = if (ret.outcome == Outcome.PARTIAL) {
        val resolved = PartialOutcomeResolver.resolve(
            PartialResolutionInput(
                outcome = ret.outcome,
                normalMoneyValueCopper = fee,
                trophiesCount = ret.trophiesCount,
                trophiesQuality = TrophiesQuality.fromCoreQuality(ret.trophiesQuality),
                suspectedTheft = ret.suspectedTheft
            )
        )
        // Keep escrow semantics stable: release deposit, then apply resolved payout.
        val reservedAfter = state.economy.reservedCopper - clientDeposit
        val moneyAfter = state.economy.moneyCopper + resolved.moneyValueCopper
        reservedAfter to moneyAfter
    } else {
        // SUCCESS: money -= fee (pay hero), reserved -= clientDeposit (unlock)
        // FAIL: reserved -= clientDeposit (unlock), money unchanged
        val reservedAfter = state.economy.reservedCopper - clientDeposit
        val moneyAfter = when (ret.outcome) {
            Outcome.FAIL -> state.economy.moneyCopper
            else -> {
                // SUCCESS: Guild pays hero the full fee from treasury
                // moneyDelta = -fee, reservedDelta = -clientDeposit
                // availableDelta = -fee - (-clientDeposit) = clientDeposit - fee
                state.economy.moneyCopper - fee
            }
        }
        reservedAfter to moneyAfter
    }

    val updatedBoard = if (board != null && board.status == BoardStatus.LOCKED) {
        val hasNonClosedActives = updatedActives.any { active -> active.boardContractId == board.id && active.status != ActiveStatus.CLOSED }
        if (!hasNonClosedActives) {
            state.contracts.board.map { b -> if (b.id == board.id) b.copy(status = BoardStatus.COMPLETED) else b }
        } else state.contracts.board
    } else state.contracts.board

    val newCompleted = state.guild.completedContractsTotal + 1
    val (newRank, contractsForNext) = calculateNextRank(newCompleted, state.guild.guildRank)

    var newGuildState = state.guild.copy(completedContractsTotal = newCompleted, contractsForNextRank = contractsForNext)
    if (newRank != state.guild.guildRank) {
        ctx.emit(
            GuildRankUp(
                day = state.meta.dayIndex,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                oldRank = state.guild.guildRank,
                newRank = newRank,
                completedContracts = newCompleted
            )
        )
        newGuildState = newGuildState.copy(guildRank = newRank)
    }

    val newState = state.copy(
        contracts = state.contracts.copy(board = updatedBoard, active = updatedActives, returns = updatedReturns),
        heroes = state.heroes.copy(roster = updatedRoster),
        economy = state.economy.copy(
            trophiesStock = newTrophiesStock,
            reservedCopper = newReservedCopper,
            moneyCopper = newMoneyCopper
        ),
        guild = newGuildState
    )

    ctx.emit(
        ReturnClosed(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            activeContractId = cmd.activeContractId.toInt()
        )
    )

    return newState
}

/**
 * Why:
 * - Tax payment is a risk-reduction lever.
 * - Penalties must be paid before principal to prevent gaming.
 *
 * How:
 * - Applies payment to penalty first, then to the base due.
 * - Emits the remaining due so adapters do not infer debt.
 */
@Suppress("UNUSED_PARAMETER")
private fun handlePayTax(
    state: GameState,
    cmd: PayTax,
    rng: Rng,
    ctx: SeqContext
): GameState {
    if (cmd.amount <= 0) return state

    val availableMoney = state.economy.moneyCopper
    if (availableMoney < cmd.amount) return state

    val moneyAfterPayment = availableMoney - cmd.amount

    // Renamed for clarity: track remaining balances after payment application
    var paymentRemaining = cmd.amount
    var penaltyRemaining = state.meta.taxPenalty
    var taxDueRemaining = state.meta.taxAmountDue

    val applyToPenalty = minOf(paymentRemaining, penaltyRemaining)
    penaltyRemaining -= applyToPenalty
    paymentRemaining -= applyToPenalty

    val applyToTax = minOf(paymentRemaining, taxDueRemaining)
    taxDueRemaining -= applyToTax
    // paymentRemaining would be reduced here but is not needed further

    val remainingDue = taxDueRemaining + penaltyRemaining
    val isPartial = remainingDue > 0

    val newState = state.copy(
        economy = state.economy.copy(moneyCopper = moneyAfterPayment),
        meta = state.meta.copy(taxAmountDue = taxDueRemaining, taxPenalty = penaltyRemaining)
    )

    ctx.emit(
        TaxPaid(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            amountPaid = cmd.amount,
            amountDue = remainingDue, // Note: this is "remaining due after payment"
            isPartialPayment = isPartial
        )
    )

    return if (!isPartial) newState.copy(meta = newState.meta.copy(taxMissedCount = 0)) else newState
}

/**
 * Why:
 * - Proof policy is a governance switch.
 * - It must be explicit to keep replays comparable.
 *
 * How:
 * - Emits a change event and updates guild policy in one step.
 */
@Suppress("UNUSED_PARAMETER")
private fun handleSetProofPolicy(
    state: GameState,
    cmd: SetProofPolicy,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val oldPolicy = state.guild.proofPolicy
    ctx.emit(
        ProofPolicyChanged(
            day = state.meta.dayIndex,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            oldPolicy = oldPolicy.ordinal,
            newPolicy = cmd.policy.ordinal
        )
    )
    return state.copy(guild = state.guild.copy(proofPolicy = cmd.policy))
}

/**
 * Why:
 * - Contract authoring is a content injection point for tools and future adapters.
 *
 * How:
 * - Creates an inbox draft and advances id counters monotonically.
 */
@Suppress("UNUSED_PARAMETER")
private fun handleCreateContract(
    state: GameState,
    cmd: CreateContract,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val newId = state.meta.ids.nextContractId
    // Use reward as clientDeposit (client's contribution towards the fee)
    val clientDeposit = cmd.reward.coerceAtLeast(0)

    val draft = ContractDraft(
        id = ContractId(newId),
        createdDay = state.meta.dayIndex,
        nextAutoResolveDay = state.meta.dayIndex + BalanceSettings.AUTO_RESOLVE_INTERVAL_DAYS,
        title = cmd.title,
        rankSuggested = cmd.rank,
        feeOffered = 0,
        salvage = cmd.salvage,
        baseDifficulty = cmd.difficulty,
        proofHint = "proof",
        clientDeposit = clientDeposit
    )

    ctx.emit(
        ContractDraftCreated(
            day = state.meta.dayIndex,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            draftId = newId,
            title = cmd.title,
            rank = cmd.rank,
            difficulty = cmd.difficulty,
            reward = cmd.reward, // Emit original value for event compatibility
            salvage = cmd.salvage
        )
    )

    return state.copy(
        contracts = state.contracts.copy(inbox = state.contracts.inbox + draft),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextContractId = newId + 1))
    )
}

/**
 * Why:
 * - Negotiation is allowed before commitment.
 * - Terms changes must preserve escrow correctness.
 *
 * How:
 * - Applies updates either in inbox or on board.
 * - Adjusts reserved copper only by the player's top-up delta.
 */
@Suppress("UNUSED_PARAMETER")
private fun handleUpdateContractTerms(
    state: GameState,
    cmd: UpdateContractTerms,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val inboxContract = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.contractId }
    val boardContract = state.contracts.board.firstOrNull { it.id.value.toLong() == cmd.contractId }

    return when {
        inboxContract != null -> {
            val feeApplied = cmd.newFee ?: inboxContract.feeOffered
            val salvageApplied = cmd.newSalvage ?: inboxContract.salvage

            val updatedDraft = inboxContract.copy(feeOffered = feeApplied, salvage = salvageApplied)

            ctx.emit(
                ContractTermsUpdated(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = inboxContract.id.value,
                    location = "inbox",
                    oldFee = inboxContract.feeOffered,
                    newFee = feeApplied, // Fixed: emit applied value, not nullable input
                    oldSalvage = inboxContract.salvage,
                    newSalvage = salvageApplied // Fixed: emit applied value, not nullable input
                )
            )

            state.copy(
                contracts = state.contracts.copy(
                    inbox = state.contracts.inbox.map { if (it.id.value.toLong() == cmd.contractId) updatedDraft else it }
                )
            )
        }

        boardContract != null -> {
            val oldFee = boardContract.fee
            val feeApplied = cmd.newFee ?: oldFee
            val salvageApplied = cmd.newSalvage ?: boardContract.salvage
            // In new accounting model, reserved holds only clientDeposit, which doesn't change when fee changes

            val updatedBoard = boardContract.copy(
                fee = feeApplied,
                salvage = salvageApplied
            )

            ctx.emit(
                ContractTermsUpdated(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = boardContract.id.value,
                    location = "board",
                    oldFee = oldFee,
                    newFee = feeApplied, // Fixed: emit applied value, not nullable input
                    oldSalvage = boardContract.salvage,
                    newSalvage = salvageApplied // Fixed: emit applied value, not nullable input
                )
            )

            state.copy(
                contracts = state.contracts.copy(
                    board = state.contracts.board.map { if (it.id.value.toLong() == cmd.contractId) updatedBoard else it }
                )
                // Note: reserved unchanged because it holds clientDeposit, not fee
            )
        }

        else -> state
    }
}

/**
 * Why:
 * - Cancellation prevents dead contracts from blocking the board.
 * - Refund rules must match the escrow model.
 *
 * How:
 * - Inbox cancellation has no economic impact.
 * - Board cancellation refunds only the player's top-up portion.
 */
@Suppress("UNUSED_PARAMETER")
private fun handleCancelContract(
    state: GameState,
    cmd: CancelContract,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val inboxContract = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.contractId }
    val boardContract = state.contracts.board.firstOrNull { it.id.value.toLong() == cmd.contractId }

    return when {
        inboxContract != null -> {
            ctx.emit(
                ContractCancelled(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = inboxContract.id.value,
                    location = "inbox",
                    refundedCopper = 0
                )
            )

            state.copy(contracts = state.contracts.copy(inbox = state.contracts.inbox.filter { it.id.value.toLong() != cmd.contractId }))
        }

        boardContract != null -> {
            // Cancel returns the client's deposit (guild had received and locked it)
            val clientDeposit = boardContract.clientDeposit

            ctx.emit(
                ContractCancelled(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = boardContract.id.value,
                    location = "board",
                    refundedCopper = clientDeposit
                )
            )

            state.copy(
                contracts = state.contracts.copy(board = state.contracts.board.filter { it.id.value.toLong() != cmd.contractId }),
                economy = state.economy.copy(
                    moneyCopper = state.economy.moneyCopper - clientDeposit,
                    reservedCopper = (state.economy.reservedCopper - clientDeposit).coerceAtLeast(0)
                )
            )
        }

        else -> state
    }
}
