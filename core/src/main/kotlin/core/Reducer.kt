package core

import core.invariants.verifyInvariants
import core.primitives.*
import core.rng.Rng
import core.state.*

private const val N_INBOX = 2
private const val N_HEROES = 2
private const val DAYS_REMAINING_INIT = 2

data class StepResult(
    val state: GameState,
    val events: List<Event>
)

/**
 * Context for building events with sequential seq numbers.
 */
class SeqContext {
    val events = mutableListOf<Event>()
    private var nextSeq = 1L

    fun emit(event: Event): Event {
        val withSeq = assignSeq(event, nextSeq++)
        events.add(withSeq)
        return withSeq
    }
}

/**
 * Pure reducer: (state, command, rng) -> (newState, events)
 * - Deterministic behavior driven only by inputs
 * - Emits only Events (no side effects)
 * - Mutates only via step(...)
 * - Runs verifyInvariants(newState) at the end of every command
 */
fun step(state: GameState, cmd: Command, rng: Rng): StepResult {
    // Validate command
    val validationResult = canApply(state, cmd)
    if (validationResult is ValidationResult.Rejected) {
        val event = CommandRejected(
            day = state.meta.dayIndex,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 1L,
            cmdType = cmd::class.simpleName ?: "Unknown",
            reason = validationResult.reason,
            detail = validationResult.detail
        )
        return StepResult(state, listOf(event))
    }

    // Increment revision once per accepted command
    val newRevision = state.meta.revision + 1
    val stateWithRevision = state.copy(
        meta = state.meta.copy(revision = newRevision)
    )

    // Dispatch to handler with seq context
    val seqCtx = SeqContext()
    val newState = when (cmd) {
        is AdvanceDay -> handleAdvanceDay(stateWithRevision, cmd, rng, seqCtx)
        is PostContract -> handlePostContract(stateWithRevision, cmd, rng, seqCtx)
        is CloseReturn -> handleCloseReturn(stateWithRevision, cmd, rng, seqCtx)
        is SellTrophies -> handleSellTrophies(stateWithRevision, cmd, rng, seqCtx)
    }

    // Verify invariants and insert before last event if it's DayEnded
    val violations = verifyInvariants(newState)
    if (violations.isNotEmpty()) {
        val lastEventIsDayEnded = seqCtx.events.lastOrNull() is DayEnded
        val insertIndex = if (lastEventIsDayEnded) seqCtx.events.size - 1 else seqCtx.events.size

        violations.forEach { violation ->
            val violationEvent = InvariantViolated(
                day = newState.meta.dayIndex,
                revision = newState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L, // will be reassigned
                invariantId = violation.invariantId,
                details = violation.details
            )
            seqCtx.events.add(insertIndex, violationEvent)
        }

        // Re-sequence all events after insertion
        seqCtx.events.forEachIndexed { index, event ->
            seqCtx.events[index] = assignSeq(event, (index + 1).toLong())
        }
    }

    return StepResult(newState, seqCtx.events)
}

@Suppress("UNUSED_PARAMETER")
private fun handleAdvanceDay(
    state: GameState,
    cmd: AdvanceDay,
    rng: Rng,
    ctx: SeqContext
): GameState {
    // Increment dayIndex first (per spec: Variant A) + clear day-scoped arrivalsToday
    val newDay = state.meta.dayIndex + 1
    var workingState = state.copy(
        meta = state.meta.copy(dayIndex = newDay),
        heroes = state.heroes.copy(arrivalsToday = emptyList())
    )

    // Emit DayStarted
    ctx.emit(
        DayStarted(
            day = newDay,
            revision = workingState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L
        )
    )

    // K9.2 — Inbox generation (fixed N=2)
    run {
        val newDrafts = ArrayList<ContractDraft>(N_INBOX)
        val contractIds = IntArray(N_INBOX)
        var nextContractId = workingState.meta.ids.nextContractId

        for (i in 0 until N_INBOX) {
            val draftId = nextContractId++
            contractIds[i] = draftId
            newDrafts.add(
                ContractDraft(
                    id = ContractId(draftId),
                    createdDay = newDay,
                    title = "Request #$draftId",
                    rankSuggested = Rank.F,
                    feeOffered = 0,
                    baseDifficulty = 1,
                    proofHint = "proof"
                )
            )
        }

        workingState = workingState.copy(
            contracts = workingState.contracts.copy(
                inbox = workingState.contracts.inbox + newDrafts.toList()
            ),
            meta = workingState.meta.copy(
                ids = workingState.meta.ids.copy(nextContractId = nextContractId)
            )
        )

        ctx.emit(
            InboxGenerated(
                day = newDay,
                revision = workingState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                count = N_INBOX,
                contractIds = contractIds
            )
        )
    }

    // K9.3 — Hero arrivals (fixed N=2)
    run {
        val newHeroes = ArrayList<Hero>(N_HEROES)
        val heroIdsRaw = IntArray(N_HEROES)
        var nextHeroId = workingState.meta.ids.nextHeroId

        for (i in 0 until N_HEROES) {
            val heroId = nextHeroId++
            heroIdsRaw[i] = heroId
            newHeroes.add(
                Hero(
                    id = HeroId(heroId),
                    name = "Hero #$heroId",
                    rank = Rank.F,
                    klass = HeroClass.WARRIOR,
                    traits = Traits(greed = 50, honesty = 50, courage = 50),
                    status = HeroStatus.AVAILABLE,
                    historyCompleted = 0
                )
            )
        }

        val arrivalsToday = heroIdsRaw.map { HeroId(it) }

        workingState = workingState.copy(
            heroes = workingState.heroes.copy(
                roster = workingState.heroes.roster + newHeroes.toList(),
                arrivalsToday = arrivalsToday
            ),
            meta = workingState.meta.copy(
                ids = workingState.meta.ids.copy(nextHeroId = nextHeroId)
            )
        )

        ctx.emit(
            HeroesArrived(
                day = newDay,
                revision = workingState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                count = N_HEROES,
                heroIds = heroIdsRaw
            )
        )
    }

    // K9.4 — Pickup (arrivals take OPEN board contracts)
    run {
        val arrivingHeroIds = workingState.heroes.arrivalsToday.sortedBy { it.value }
        var nextActiveContractId = workingState.meta.ids.nextActiveContractId

        val updatedBoard = workingState.contracts.board.toMutableList()
        val updatedRoster = workingState.heroes.roster.toMutableList()
        val newActiveContracts = mutableListOf<ActiveContract>()

        // Precompute open board indices in sorted order to avoid sorting per hero (O(B log B) once)
        val openIndices = updatedBoard
            .withIndex()
            .filter { it.value.status == BoardStatus.OPEN }
            .sortedBy { it.value.id.value }
            .map { it.index }

        // Build map from HeroId to roster index to avoid repeated indexOfFirst scans
        val rosterIndexById = updatedRoster.mapIndexed { idx, h -> h.id to idx }.toMap().toMutableMap()

        var openPos = 0

        for (heroId in arrivingHeroIds) {
            val openIdx = if (openPos < openIndices.size) openIndices[openPos++ ] else null

            if (openIdx == null) continue

            val selectedBoard = updatedBoard[openIdx]
            val activeId = nextActiveContractId++

            newActiveContracts.add(
                ActiveContract(
                    id = ActiveContractId(activeId),
                    boardContractId = selectedBoard.id,
                    takenDay = newDay,
                    daysRemaining = DAYS_REMAINING_INIT,
                    heroIds = listOf(heroId),
                    status = ActiveStatus.WIP
                )
            )

            // Mark board as locked
            updatedBoard[openIdx] = selectedBoard.copy(status = BoardStatus.LOCKED)

            // Update hero status using precomputed index map
            val heroIndex = rosterIndexById[heroId]
            if (heroIndex != null) {
                updatedRoster[heroIndex] = updatedRoster[heroIndex].copy(status = HeroStatus.ON_MISSION)
                // index map remains valid since we only update elements in-place
            }

            ctx.emit(
                ContractTaken(
                    day = newDay,
                    revision = workingState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    activeContractId = activeId,
                    boardContractId = selectedBoard.id.value,
                    heroIds = intArrayOf(heroId.value),
                    daysRemaining = DAYS_REMAINING_INIT
                )
            )
        }

        workingState = workingState.copy(
            contracts = workingState.contracts.copy(
                board = updatedBoard,
                active = workingState.contracts.active + newActiveContracts
            ),
            heroes = workingState.heroes.copy(roster = updatedRoster),
            meta = workingState.meta.copy(
                ids = workingState.meta.ids.copy(nextActiveContractId = nextActiveContractId)
            )
        )
    }

    // K9.5 — WIP advance + resolve
    var successfulReturns = 0
    var failedReturns = 0
    
    run {
        val sortedActives = workingState.contracts.active.sortedBy { it.id.value }
        val updatedActives = mutableListOf<ActiveContract>()
        val newReturns = mutableListOf<ReturnPacket>()

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
                val outcome = Outcome.SUCCESS
                val requiresPlayerClose = true

                // Generate trophies based on outcome
                val trophiesCount = if (outcome == Outcome.SUCCESS) {
                    1 + rng.nextInt(3) // [1..3]: nextInt(3) gives [0,1,2], then +1
                } else {
                    0 // FAILURE gives 0 trophies
                }

                // Generate trophy quality
                val qualities = Quality.entries.toTypedArray()
                val trophiesQuality = qualities[rng.nextInt(qualities.size)]

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
                        requiresPlayerClose = requiresPlayerClose
                    )
                )

                // Count eligible returns for stability calculation
                if (!requiresPlayerClose) {
                    if (outcome == Outcome.SUCCESS) {
                        successfulReturns++
                    } else if (outcome == Outcome.FAIL) {
                        failedReturns++
                    }
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

                // Update active to RETURN_READY status if player close is required
                if (requiresPlayerClose) {
                    updatedActives.add(active.copy(daysRemaining = 0, status = ActiveStatus.RETURN_READY))
                }
                // Otherwise, remove from active list (auto-closed)
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
    }

    // Stability update (before DayEnded)
    run {
        val oldStability = workingState.region.stability
        val delta = successfulReturns - failedReturns
        val newStability = (oldStability + delta).coerceIn(0, 100)

        if (newStability != oldStability) {
            workingState = workingState.copy(
                region = workingState.region.copy(stability = newStability)
            )

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
    }

    // Snapshot
    val snapshot = DaySnapshot(
        day = newDay,
        revision = workingState.meta.revision,
        money = workingState.economy.moneyCopper,
        trophies = workingState.economy.trophiesStock,
        regionStability = workingState.region.stability,
        guildReputation = workingState.guild.reputation,
        inboxCount = workingState.contracts.inbox.size,
        boardCount = workingState.contracts.board.size,
        activeCount = workingState.contracts.active.count { it.status == ActiveStatus.WIP },
        returnsNeedingCloseCount = workingState.contracts.returns.count { it.requiresPlayerClose }
    )

    // Emit DayEnded (always last for AdvanceDay)
    ctx.emit(
        DayEnded(
            day = newDay,
            revision = workingState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            snapshot = snapshot
        )
    )

    return workingState
}

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
        salvage = SalvagePolicy.GUILD,
        status = BoardStatus.OPEN
    )

    val newState = state.copy(
        contracts = state.contracts.copy(
            inbox = state.contracts.inbox.filter { it.id.value.toLong() != cmd.inboxId },
            board = state.contracts.board + boardContract
        ),
        economy = state.economy.copy(
            reservedCopper = state.economy.reservedCopper + fee
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
            salvage = SalvagePolicy.GUILD
        )
    )

    return newState
}

@Suppress("UNUSED_PARAMETER")
private fun handleCloseReturn(
    state: GameState,
    cmd: CloseReturn,
    rng: Rng,
    ctx: SeqContext
): GameState {
    // Locate entities (canApply already validated existence)
    val ret = state.contracts.returns.firstOrNull { it.activeContractId.value.toLong() == cmd.activeContractId } ?: return state

    // Find board contract for salvage policy and fee payout
    val board = state.contracts.board.firstOrNull { it.id == ret.boardContractId }
    val fee = board?.fee ?: 0

    // Update heroes: status to AVAILABLE, historyCompleted++
    val heroIdSet = ret.heroIds.map { it.value }.toSet()
    val updatedRoster = state.heroes.roster.map { hero ->
        if (heroIdSet.contains(hero.id.value)) {
            hero.copy(
                status = HeroStatus.AVAILABLE,
                historyCompleted = hero.historyCompleted + 1
            )
        } else {
            hero
        }
    }

    // Remove return packet
    val updatedReturns = state.contracts.returns.filter { it.activeContractId.value.toLong() != cmd.activeContractId }

    // Update active contract status to CLOSED
    val updatedActives = state.contracts.active.map { active ->
        if (active.id.value.toLong() == cmd.activeContractId) {
            active.copy(status = ActiveStatus.CLOSED)
        } else {
            active
        }
    }

    // Apply salvage/trophy accounting & payout fee
    var newTrophiesStock = state.economy.trophiesStock
    if (board != null && board.salvage != SalvagePolicy.HERO) {
        newTrophiesStock += ret.trophiesCount
    }

    val newReservedCopper = state.economy.reservedCopper - fee
    val newMoneyCopper = state.economy.moneyCopper - fee

    // Check if board should be unlocked (LOCKED -> COMPLETED)
    // Board should only unlock when ALL actives referencing it are CLOSED
    val updatedBoard = if (board != null && board.status == BoardStatus.LOCKED) {
        // Check if there are any non-CLOSED actives referencing this board
        val hasNonClosedActives = updatedActives.any { active ->
            active.boardContractId == board.id && active.status != ActiveStatus.CLOSED
        }

        // Only unlock if all actives are CLOSED
        if (!hasNonClosedActives) {
            state.contracts.board.map { b ->
                if (b.id == board.id) b.copy(status = BoardStatus.COMPLETED) else b
            }
        } else {
            state.contracts.board
        }
    } else {
        state.contracts.board
    }

    val newState = state.copy(
        contracts = state.contracts.copy(
            board = updatedBoard,
            active = updatedActives,
            returns = updatedReturns
        ),
        heroes = state.heroes.copy(roster = updatedRoster),
        economy = state.economy.copy(
            trophiesStock = newTrophiesStock,
            reservedCopper = newReservedCopper,
            moneyCopper = newMoneyCopper
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

@Suppress("UNUSED_PARAMETER")
private fun handleSellTrophies(
    state: GameState,
    cmd: SellTrophies,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val total = state.economy.trophiesStock

    if (total <= 0) {
        return state
    }

    val amountToSell = if (cmd.amount <= 0) {
        total
    } else {
        minOf(cmd.amount, total)
    }

    if (amountToSell <= 0) {
        return state
    }

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
    }
}
