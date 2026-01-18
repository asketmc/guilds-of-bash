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
    val rejection = canApply(state, cmd)
    if (rejection != null) {
        val event = CommandRejected(
            day = state.meta.dayIndex,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 1L,
            cmdType = cmd::class.simpleName ?: "Unknown",
            reason = rejection.reason,
            detail = rejection.detail
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

        for (heroId in arrivingHeroIds) {
            val openIdx = updatedBoard
                .withIndex()
                .sortedBy { it.value.id.value }
                .firstOrNull { it.value.status == BoardStatus.OPEN }
                ?.index

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

            updatedBoard[openIdx] = selectedBoard.copy(status = BoardStatus.LOCKED)

            val heroIndex = updatedRoster.indexOfFirst { it.id == heroId }
            if (heroIndex >= 0) {
                updatedRoster[heroIndex] = updatedRoster[heroIndex].copy(status = HeroStatus.ON_MISSION)
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
                newReturns.add(
                    ReturnPacket(
                        activeContractId = active.id,
                        resolvedDay = newDay,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 0,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
                    )
                )

                ctx.emit(
                    ContractResolved(
                        day = newDay,
                        revision = workingState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        activeContractId = active.id.value,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 0,
                        quality = Quality.OK,
                        reasonTags = intArrayOf()
                    )
                )

                updatedActives.add(
                    active.copy(
                        daysRemaining = newDaysRemaining,
                        status = ActiveStatus.RETURN_READY
                    )
                )
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
        activeCount = workingState.contracts.active.size,
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
    val draft = state.contracts.inbox.firstOrNull { it.id == cmd.inboxId } ?: return state

    val boardContract = BoardContract(
        id = draft.id,
        postedDay = state.meta.dayIndex,
        title = draft.title,
        rank = cmd.rank,
        fee = cmd.fee,
        salvage = cmd.salvage,
        status = BoardStatus.OPEN
    )

    val newState = state.copy(
        contracts = state.contracts.copy(
            inbox = state.contracts.inbox.filter { it.id != cmd.inboxId },
            board = state.contracts.board + boardContract
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
            rank = cmd.rank,
            fee = cmd.fee,
            salvage = cmd.salvage
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
    val active = state.contracts.active.firstOrNull { it.id == cmd.activeContractId } ?: return state
    val ret = state.contracts.returns.firstOrNull { it.activeContractId == cmd.activeContractId } ?: return state

    // Find board contract for salvage policy
    val board = state.contracts.board.firstOrNull { it.id == active.boardContractId }

    // Update active contract status
    val updatedActive = state.contracts.active.map { ac ->
        if (ac.id == cmd.activeContractId) ac.copy(status = ActiveStatus.CLOSED) else ac
    }

    // Update heroes: status to AVAILABLE, historyCompleted++
    val updatedRoster = state.heroes.roster.map { hero ->
        if (active.heroIds.contains(hero.id)) {
            hero.copy(
                status = HeroStatus.AVAILABLE,
                historyCompleted = hero.historyCompleted + 1
            )
        } else {
            hero
        }
    }

    // Remove return packet (decision: remove on close)
    val updatedReturns = state.contracts.returns.filter { it.activeContractId != cmd.activeContractId }

    // Apply salvage/trophy accounting
    var newTrophiesStock = state.economy.trophiesStock
    if (board != null && board.salvage != SalvagePolicy.HERO) {
        newTrophiesStock += ret.trophiesCount
    }

    val newState = state.copy(
        contracts = state.contracts.copy(
            active = updatedActive,
            returns = updatedReturns
        ),
        heroes = state.heroes.copy(roster = updatedRoster),
        economy = state.economy.copy(trophiesStock = newTrophiesStock)
    )

    ctx.emit(
        ReturnClosed(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            activeContractId = cmd.activeContractId.value
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
        is DayEnded -> event.copy(seq = seq)
        is CommandRejected -> event.copy(seq = seq)
        is InvariantViolated -> event.copy(seq = seq)
    }
}
