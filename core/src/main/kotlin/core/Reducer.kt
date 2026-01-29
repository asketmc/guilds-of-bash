// FILE: core/src/main/kotlin/core/Reducer.kt
package core

import core.invariants.verifyInvariants
import core.primitives.*
import core.rng.Rng
import core.state.*

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

// (no file-level constants)

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

    val (inboxMult, heroesMult) = core.pipeline.GuildProgression.getRankMultipliers(workingState.guild.guildRank)
    val nInbox = inboxMult * 2
    val nHeroes = heroesMult * 2

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
    val result = core.pipeline.InboxLifecycle.generateDrafts(
        count = nInbox,
        currentDay = newDay,
        stability = state.region.stability,
        startingContractId = state.meta.ids.nextContractId,
        rng = rng
    )

    ctx.emit(
        InboxGenerated(
            day = newDay,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            count = nInbox,
            contractIds = result.contractIds
        )
    )

    return state.copy(
        contracts = state.contracts.copy(inbox = state.contracts.inbox + result.drafts),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextContractId = result.nextContractId))
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
    rng: Rng,
    ctx: SeqContext,
    newDay: Int,
    nHeroes: Int
): GameState {
    val result = core.pipeline.HeroSupplyModel.generateArrivals(
        count = nHeroes,
        startingHeroId = state.meta.ids.nextHeroId,
        rng = rng
    )

    ctx.emit(
        HeroesArrived(
            day = newDay,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            count = nHeroes,
            heroIds = result.heroIds
        )
    )

    return state.copy(
        heroes = state.heroes.copy(
            roster = state.heroes.roster + result.heroes,
            arrivalsToday = result.arrivalIds
        ),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextHeroId = result.nextHeroId))
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
    val result = core.pipeline.ContractPickupModel.computePickups(
        arrivingHeroIds = state.heroes.arrivalsToday,
        roster = state.heroes.roster,
        board = state.contracts.board,
        currentDay = newDay,
        startingActiveContractId = state.meta.ids.nextActiveContractId
    )

    // Emit events for each decision
    for (decision in result.decisions) {
        when (decision.decision) {
            core.pipeline.PickupDecisionType.DECLINED -> {
                ctx.emit(
                    HeroDeclined(
                        day = newDay,
                        revision = state.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        heroId = decision.heroId.value,
                        boardContractId = decision.boardContractId!!.value,
                        reason = decision.declineReason ?: "unknown"
                    )
                )
            }
            core.pipeline.PickupDecisionType.ACCEPTED -> {
                ctx.emit(
                    ContractTaken(
                        day = newDay,
                        revision = state.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        activeContractId = decision.activeContractId!!.value,
                        boardContractId = decision.boardContractId!!.value,
                        heroIds = intArrayOf(decision.heroId.value),
                        daysRemaining = BalanceSettings.DAYS_REMAINING_INIT
                    )
                )
            }
            core.pipeline.PickupDecisionType.NO_CONTRACT -> { /* No event */ }
        }
    }

    return state.copy(
        contracts = state.contracts.copy(
            board = result.updatedBoard,
            active = state.contracts.active + result.newActives
        ),
        heroes = state.heroes.copy(roster = result.updatedRoster),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextActiveContractId = result.nextActiveContractId))
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

    // Step 1: Advance WIP days using WipProgression module
    // Only WIP actives participate in day countdown and completion.
    val (wipActives, nonWipActives) = workingState.contracts.active.partition { it.status == ActiveStatus.WIP }
    val wipResult = core.pipeline.WipProgression.advance(wipActives)

    // Emit WipAdvanced events
    for (advance in wipResult.advances) {
        ctx.emit(
            WipAdvanced(
                day = newDay,
                revision = workingState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                activeContractId = advance.activeContractId,
                daysRemaining = advance.newDaysRemaining
            )
        )
    }

    val updatedActives = (wipResult.updatedActives + nonWipActives)
        .sortedBy { it.id.value }
        .toMutableList()
    val newReturns = mutableListOf<ReturnPacket>()

    // Pre-build lookup maps for O(1) access
    var boardById = workingState.contracts.board.associateBy { it.id }
    val heroById = workingState.heroes.roster.associateBy { it.id }

    // Step 2: Resolve completed contracts
    for (activeId in wipResult.completedActiveIds) {
        val active = updatedActives.first { it.id.value == activeId }

        // Cache lookups once for this active contract
        val heroId = active.heroIds.firstOrNull()
        val hero = heroId?.let { heroById[it] }
        val boardContract = boardById[active.boardContractId]
        val contractDifficulty = boardContract?.baseDifficulty ?: 1

        // Decision: Outcome resolution
        val outcomeDecision = core.pipeline.OutcomeResolution.decide(hero, contractDifficulty, rng)

        val effectiveOutcome = outcomeDecision.outcome

        // Decision: Theft resolution
        // DEATH is handled as fail-like with no theft and no trophies.
        val baseTheftDecision = core.pipeline.TheftModel.decide(
            hero,
            boardContract,
            outcomeDecision.baseTrophiesCount,
            rng
        )
        val theftDecision = if (effectiveOutcome == Outcome.DEATH) {
            baseTheftDecision.copy(
                theftOccurred = false,
                expectedTrophiesCount = 0,
                reportedTrophiesCount = 0
            )
        } else baseTheftDecision

        // Emit theft event if occurred
        if (theftDecision.theftOccurred && heroId != null) {
            ctx.emit(
                TrophyTheftSuspected(
                    day = newDay,
                    revision = workingState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    activeContractId = active.id.value,
                    heroId = heroId.value,
                    expectedTrophies = theftDecision.expectedTrophiesCount,
                    reportedTrophies = theftDecision.reportedTrophiesCount
                )
            )
        }

        // Track success/fail for stability
        if (!outcomeDecision.requiresPlayerClose) {
            if (effectiveOutcome == Outcome.SUCCESS) successfulReturns++
            else if (effectiveOutcome == Outcome.FAIL || effectiveOutcome == Outcome.DEATH) failedReturns++
        }

        // Emit ContractResolved event
        ctx.emit(
            ContractResolved(
                day = newDay,
                revision = workingState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                activeContractId = active.id.value,
                outcome = effectiveOutcome,
                trophiesCount = theftDecision.reportedTrophiesCount,
                quality = outcomeDecision.trophiesQuality,
                reasonTags = intArrayOf()
            )
        )

        // Create return packet for ALL resolves (both manual-close and auto-close).
        // This is the canonical journal of resolution; requiresPlayerClose distinguishes semantics.
        newReturns.add(
            ReturnPacket(
                activeContractId = active.id,
                boardContractId = active.boardContractId,
                heroIds = active.heroIds,
                resolvedDay = newDay,
                outcome = effectiveOutcome,
                trophiesCount = theftDecision.reportedTrophiesCount,
                trophiesQuality = outcomeDecision.trophiesQuality,
                reasonTags = emptyList(),
                requiresPlayerClose = outcomeDecision.requiresPlayerClose,
                suspectedTheft = theftDecision.theftOccurred
            )
        )

        // Branch: player-close vs auto-close
        if (outcomeDecision.requiresPlayerClose) {
            // Mark as RETURN_READY for manual closure
            val idx = updatedActives.indexOfFirst { it.id.value == activeId }
            if (idx >= 0) {
                updatedActives[idx] = updatedActives[idx].copy(daysRemaining = 0, status = ActiveStatus.RETURN_READY)
            }
        } else {
            // Auto-close path: apply all settlements immediately

            // Settlement: Economy
            val economyDelta = core.pipeline.EconomySettlement.computeAutoCloseDelta(
                effectiveOutcome,
                boardContract,
                theftDecision.reportedTrophiesCount,
                workingState.economy
            )

            // Settlement: Hero lifecycle (requiresPlayerClose=false -> hero becomes AVAILABLE)
            val heroResult = core.pipeline.HeroLifecycle.computePostResolution(
                active.heroIds,
                effectiveOutcome,
                workingState.heroes.roster,
                workingState.heroes.arrivalsToday,
                requiresPlayerClose = false
            )

            // Emit HeroDied events if any
            for (hid in heroResult.diedHeroIds) {
                ctx.emit(
                    HeroDied(
                        day = workingState.meta.dayIndex,
                        revision = workingState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        heroId = hid,
                        activeContractId = active.id.value,
                        boardContractId = (boardContract?.id?.value ?: active.boardContractId.value)
                    )
                )
            }

            // Settlement: Board status
            val updatedBoard = if (boardContract != null && boardContract.status == BoardStatus.LOCKED) {
                // Auto-close closes the only active for this board, so the board can complete.
                workingState.contracts.board.map { b ->
                    if (b.id == boardContract.id) b.copy(status = BoardStatus.COMPLETED) else b
                }
            } else workingState.contracts.board

            // Settlement: Guild progression
            val guildResult = core.pipeline.GuildProgression.computeAfterCompletion(
                workingState.guild.completedContractsTotal,
                workingState.guild.guildRank
            )

            if (guildResult.rankChanged) {
                ctx.emit(
                    GuildRankUp(
                        day = workingState.meta.dayIndex,
                        revision = workingState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        oldRank = guildResult.oldRank,
                        newRank = guildResult.newGuildRank,
                        completedContracts = guildResult.newCompletedContractsTotal
                    )
                )
            }

            // Keep arrivalsToday consistent: arrivalsToday must be subset of roster.
            val rosterIdSet = heroResult.updatedRoster.map { it.id }.toHashSet()
            val safeArrivalsToday = heroResult.updatedArrivalsToday.filter { it in rosterIdSet }

            // Apply all settlements to working state
            workingState = workingState.copy(
                contracts = workingState.contracts.copy(board = updatedBoard),
                heroes = workingState.heroes.copy(
                    roster = heroResult.updatedRoster,
                    arrivalsToday = safeArrivalsToday
                ),
                economy = economyDelta.applyTo(workingState.economy),
                guild = workingState.guild.copy(
                    completedContractsTotal = guildResult.newCompletedContractsTotal,
                    contractsForNextRank = guildResult.contractsForNextRank,
                    guildRank = guildResult.newGuildRank
                )
            )

            // Auto-close means the active is fully closed (no manual CloseReturn expected).
            val idx = updatedActives.indexOfFirst { it.id.value == activeId }
            if (idx >= 0) {
                updatedActives[idx] = updatedActives[idx].copy(daysRemaining = 0, status = ActiveStatus.CLOSED)
            }

            ctx.emit(
                ReturnClosed(
                    day = workingState.meta.dayIndex,
                    revision = workingState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    activeContractId = active.id.value
                )
            )

            // Keep board lookup in sync after mutation.
            boardById = workingState.contracts.board.associateBy { it.id }
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
    val update = core.pipeline.StabilityModel.computeFromResults(
        successfulReturns = successfulReturns,
        failedReturns = failedReturns,
        oldStability = state.region.stability
    )

    if (!update.changed) return state

    ctx.emit(
        StabilityUpdated(
            day = newDay,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            oldStability = update.oldStability,
            newStability = update.newStability
        )
    )

    return state.copy(region = state.region.copy(stability = update.newStability))
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
    val eval = core.pipeline.TaxPolicy.evaluateEndOfDay(
        currentDay = newDay,
        taxDueDay = state.meta.taxDueDay,
        taxAmountDue = state.meta.taxAmountDue,
        taxPenalty = state.meta.taxPenalty,
        taxMissedCount = state.meta.taxMissedCount,
        guildRank = state.guild.guildRank
    ) ?: return state

    val newState = state.copy(
        meta = state.meta.copy(
            taxPenalty = eval.newTaxPenalty,
            taxMissedCount = eval.newMissedCount,
            taxDueDay = eval.newTaxDueDay,
            taxAmountDue = eval.newTaxAmountDue
        )
    )

    when (eval.type) {
        core.pipeline.TaxEvaluationType.MISSED -> {
            ctx.emit(
                TaxMissed(
                    day = newDay,
                    revision = newState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    amountDue = eval.amountDue,
                    penaltyAdded = eval.penaltyAdded,
                    missedCount = eval.newMissedCount
                )
            )

            if (eval.shutdownTriggered) {
                ctx.emit(
                    GuildShutdown(
                        day = newDay,
                        revision = newState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        reason = "tax_evasion"
                    )
                )
            }
        }
        core.pipeline.TaxEvaluationType.DUE_SCHEDULED -> {
            ctx.emit(
                TaxDue(
                    day = newDay,
                    revision = newState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    amountDue = eval.amountDue,
                    dueDay = eval.newTaxDueDay
                )
            )
        }
    }

    return newState
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
    @Suppress("UNUSED_PARAMETER") rng: Rng,
    ctx: SeqContext
): GameState {
    val total = state.economy.trophiesStock
    if (total <= 0) return state

    val amountToSell = if (cmd.amount <= 0) total else minOf(cmd.amount, total)
    if (amountToSell <= 0) return state

    // Settlement: Trophy sale
    val economyDelta = core.pipeline.EconomySettlement.computeTrophySaleDelta(amountToSell)

    val newState = state.copy(
        economy = economyDelta.applyTo(state.economy)
    )

    ctx.emit(
        TrophySold(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            amount = amountToSell,
            moneyGained = amountToSell  // 1:1 exchange rate
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
    @Suppress("UNUSED_PARAMETER") rng: Rng,
    ctx: SeqContext
): GameState {
    val draft = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.inboxId } ?: return state

    val boardContract = BoardContract(
        id = draft.id,
        postedDay = state.meta.dayIndex,
        title = draft.title,
        rank = draft.rankSuggested,
        fee = cmd.fee,
        salvage = cmd.salvage,
        baseDifficulty = draft.baseDifficulty,
        status = BoardStatus.OPEN,
        clientDeposit = draft.clientDeposit
    )

    // Settlement: Economy delta for posting
    val economyDelta = core.pipeline.EconomySettlement.computePostContractDelta(draft.clientDeposit)

    val newState = state.copy(
        contracts = state.contracts.copy(
            inbox = state.contracts.inbox.filter { it.id.value.toLong() != cmd.inboxId },
            board = state.contracts.board + boardContract
        ),
        economy = economyDelta.applyTo(state.economy)
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
            fee = cmd.fee,
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
    @Suppress("UNUSED_PARAMETER") rng: Rng,
    ctx: SeqContext
): GameState {
    val ret = state.contracts.returns.firstOrNull { it.activeContractId.value.toLong() == cmd.activeContractId } ?: return state
    val board = state.contracts.board.firstOrNull { it.id == ret.boardContractId }

    // Policy: Check if closure is allowed
    val closureCheck = core.pipeline.ReturnClosurePolicy.canClose(
        state.guild.proofPolicy,
        ret.trophiesQuality,
        ret.suspectedTheft
    )
    if (!closureCheck.allowed) return state

    // Settlement: Hero lifecycle
    val updatedRoster = core.pipeline.HeroLifecycle.computeManualCloseUpdate(
        ret.heroIds,
        state.heroes.roster
    )

    // Filter out this return and mark active as CLOSED
    val updatedReturns = state.contracts.returns.filter { it.activeContractId.value.toLong() != cmd.activeContractId }
    val updatedActives = state.contracts.active.map { active ->
        if (active.id.value.toLong() == cmd.activeContractId) active.copy(status = ActiveStatus.CLOSED) else active
    }

    // Settlement: Economy
    val economyDelta = core.pipeline.EconomySettlement.computeManualCloseDelta(
        ret.outcome,
        board,
        ret.trophiesCount,
        ret.trophiesQuality,
        ret.suspectedTheft,
        state.economy
    )

    // Settlement: Board status
    val updatedBoard = if (board != null && board.status == BoardStatus.LOCKED) {
        val hasNonClosedActives = updatedActives.any { active ->
            active.boardContractId == board.id && active.status != ActiveStatus.CLOSED
        }
        if (!hasNonClosedActives) {
            state.contracts.board.map { b ->
                if (b.id == board.id) b.copy(status = BoardStatus.COMPLETED) else b
            }
        } else state.contracts.board
    } else state.contracts.board

    // Settlement: Guild progression
    val guildResult = core.pipeline.GuildProgression.computeAfterCompletion(
        state.guild.completedContractsTotal,
        state.guild.guildRank
    )

    if (guildResult.rankChanged) {
        ctx.emit(
            GuildRankUp(
                day = state.meta.dayIndex,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                oldRank = guildResult.oldRank,
                newRank = guildResult.newGuildRank,
                completedContracts = guildResult.newCompletedContractsTotal
            )
        )
    }

    // Apply all settlements
    val newState = state.copy(
        contracts = state.contracts.copy(
            board = updatedBoard,
            active = updatedActives,
            returns = updatedReturns
        ),
        heroes = state.heroes.copy(roster = updatedRoster),
        economy = economyDelta.applyTo(state.economy),
        guild = state.guild.copy(
            completedContractsTotal = guildResult.newCompletedContractsTotal,
            contractsForNextRank = guildResult.contractsForNextRank,
            guildRank = guildResult.newGuildRank
        )
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
    @Suppress("UNUSED_PARAMETER") rng: Rng,
    ctx: SeqContext
): GameState {
    if (cmd.amount <= 0) return state

    val availableMoney = state.economy.moneyCopper
    if (availableMoney < cmd.amount) return state

    // Settlement: Tax payment
    val paymentResult = core.pipeline.TaxPolicy.computePayment(
        paymentAmount = cmd.amount,
        currentTaxDue = state.meta.taxAmountDue,
        currentPenalty = state.meta.taxPenalty
    )

    val newState = state.copy(
        economy = state.economy.copy(moneyCopper = availableMoney - cmd.amount),
        meta = state.meta.copy(
            taxAmountDue = paymentResult.newTaxDue,
            taxPenalty = paymentResult.newPenalty,
            taxMissedCount = if (paymentResult.isComplete) 0 else state.meta.taxMissedCount
        )
    )

    ctx.emit(
        TaxPaid(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            amountPaid = cmd.amount,
            amountDue = paymentResult.remainingDue,
            isPartialPayment = !paymentResult.isComplete
        )
    )

    return newState
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
    @Suppress("UNUSED_PARAMETER") rng: Rng,
    ctx: SeqContext
): GameState {
    val change = core.pipeline.GovernancePolicy.computePolicyChange(
        oldPolicy = state.guild.proofPolicy,
        newPolicy = cmd.policy
    )

    if (change.changed) {
        ctx.emit(
            ProofPolicyChanged(
                day = state.meta.dayIndex,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                oldPolicy = change.oldPolicy.ordinal,
                newPolicy = change.newPolicy.ordinal
            )
        )
    }

    return state.copy(guild = state.guild.copy(proofPolicy = change.newPolicy))
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
    @Suppress("UNUSED_PARAMETER") rng: Rng,
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
            // Settlement: Economy delta for cancellation
            val economyDelta = core.pipeline.EconomySettlement.computeCancelContractDelta(boardContract.clientDeposit)

            ctx.emit(
                ContractCancelled(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = boardContract.id.value,
                    location = "board",
                    refundedCopper = boardContract.clientDeposit
                )
            )

            state.copy(
                contracts = state.contracts.copy(board = state.contracts.board.filter { it.id.value.toLong() != cmd.contractId }),
                economy = economyDelta.applyTo(state.economy)
            )
        }

        else -> state
    }
}
