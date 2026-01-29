// FILE: core/src/main/kotlin/core/handlers/AdvanceDayHandler.kt
package core.handlers

import core.*
import core.pipeline.*
import core.primitives.*
import core.rng.Rng
import core.state.*

/**
 * AdvanceDay command handler and all day phases.
 *
 * ## Semantic Ownership
 * Handles the complete day advancement pipeline including:
 * - Inbox generation
 * - Hero arrivals
 * - Auto-resolve stale drafts
 * - Contract pickup
 * - WIP progression and resolution
 * - Stability updates
 * - Tax evaluation
 * - Day-end snapshot
 *
 * ## Visibility
 * Internal to core module - only Reducer.kt should call these.
 *
 * ## RNG Draw Order Contract (CRITICAL FOR REPLAY DETERMINISM)
 * The following RNG draws occur in this exact order during AdvanceDay:
 * 1. phaseInboxGeneration: N draws for draft difficulty (N = nInbox)
 * 2. phaseHeroArrivals: M draws for hero traits (M = nHeroes * traits_per_hero)
 * 3. phaseAutoResolveInbox: K draws for bucket decisions (K = due drafts count)
 * 4. phasePickup: 0 draws (deterministic)
 * 5. phaseWipAndResolve: Per completed contract:
 *    - 1-3 draws for outcome resolution
 *    - 1 draw for theft decision
 *
 * DO NOT reorder phases or add RNG draws without updating this contract.
 */

/**
 * Result of WIP and resolution phase.
 */
data class WipResolveResult(
    val state: GameState,
    val successfulReturns: Int,
    val failedReturns: Int
)

/**
 * Main AdvanceDay handler - orchestrates all day phases.
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
internal fun handleAdvanceDay(
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

    val (inboxMult, heroesMult) = GuildProgression.getRankMultipliers(workingState.guild.guildRank)
    val nInbox = inboxMult * BalanceSettings.RANK_MULTIPLIER_BASE
    val nHeroes = heroesMult * BalanceSettings.RANK_MULTIPLIER_BASE

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

// ─────────────────────────────────────────────────────────────────────────────
// Day Phases
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Phase 1: Inbox Generation
 *
 * Why:
 * - Inbox exists to decouple content generation from player publication decisions.
 *
 * How:
 * - Generates drafts using stability-driven threat scaling and seeded RNG.
 * - Advances id counters in one direction only.
 *
 * RNG: N draws for draft difficulty (N = nInbox)
 */
private fun phaseInboxGeneration(
    state: GameState,
    cmd: AdvanceDay,
    rng: Rng,
    ctx: SeqContext,
    newDay: Int,
    nInbox: Int
): GameState {
    val result = InboxLifecycle.generateDrafts(
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
 * Phase 2: Hero Arrivals
 *
 * Why:
 * - Heroes are agents. Arrivals are the simulation's supply side.
 *
 * How:
 * - Appends new heroes and records their ids as "arrivalsToday" to lock ordering.
 *
 * RNG: M draws for hero traits (M = nHeroes * traits_per_hero)
 */
private fun phaseHeroArrivals(
    state: GameState,
    cmd: AdvanceDay,
    rng: Rng,
    ctx: SeqContext,
    newDay: Int,
    nHeroes: Int
): GameState {
    val result = HeroSupplyModel.generateArrivals(
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
 * Phase 3: Auto-Resolve Inbox
 *
 * Why:
 * - The world must not accumulate stale drafts forever.
 * - Ignored requests must have a measurable systemic cost.
 *
 * How:
 * - Delegates bucket decision to AutoResolveModel.
 * - Emits events and applies stability adjustment.
 *
 * RNG: K draws for bucket decisions (K = due drafts count)
 */
private fun phaseAutoResolveInbox(
    state: GameState,
    cmd: AdvanceDay,
    rng: Rng,
    ctx: SeqContext,
    newDay: Int
): GameState {
    val result = AutoResolveModel.computeAutoResolve(
        inbox = state.contracts.inbox,
        currentDay = newDay,
        rng = rng
    )

    if (result.decisions.isEmpty()) return state

    // Emit events for each decision
    for (decision in result.decisions) {
        ctx.emit(
            ContractAutoResolved(
                day = newDay,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                draftId = decision.draftId.value,
                bucket = decision.bucket
            )
        )
    }

    var workingState = state.copy(
        contracts = state.contracts.copy(inbox = result.updatedInbox)
    )

    if (result.cumulativeStabilityDelta != 0) {
        val oldStability = workingState.region.stability
        val newStability = (oldStability + result.cumulativeStabilityDelta).coerceIn(
            BalanceSettings.STABILITY_MIN, BalanceSettings.STABILITY_MAX
        )
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
 * Phase 4: Contract Pickup
 *
 * Why:
 * - Contract pickup is the core agent decision loop.
 * - The player does not micro-manage; the system must select and explain.
 *
 * How:
 * - Iterates arriving heroes in deterministic id order.
 * - Locks the chosen board contract before creating the active contract.
 *
 * RNG: 0 draws (deterministic selection)
 */
private fun phasePickup(
    state: GameState,
    cmd: AdvanceDay,
    @Suppress("UNUSED_PARAMETER") rng: Rng,
    ctx: SeqContext,
    newDay: Int
): GameState {
    val result = ContractPickupModel.computePickups(
        arrivingHeroIds = state.heroes.arrivalsToday,
        roster = state.heroes.roster,
        board = state.contracts.board,
        currentDay = newDay,
        startingActiveContractId = state.meta.ids.nextActiveContractId
    )

    // Emit events for each decision
    for (decision in result.decisions) {
        when (decision.decision) {
            PickupDecisionType.DECLINED -> {
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
            PickupDecisionType.ACCEPTED -> {
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
            PickupDecisionType.NO_CONTRACT -> { /* No event */ }
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
 * Phase 5: WIP Advancement and Resolution
 *
 * Why:
 * - WIP advancement and resolution must be serial and stable to keep replay valid.
 * - Auto-closure must apply economy, rank, and hero lifecycle rules without adapter logic.
 *
 * How:
 * - Processes actives in deterministic id order.
 * - Emits intermediate and terminal events in causal order.
 *
 * RNG: Per completed contract: 1-3 draws for outcome, 1 draw for theft
 */
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
    val (wipActives, nonWipActives) = workingState.contracts.active.partition { it.status == ActiveStatus.WIP }
    val wipResult = WipProgression.advance(wipActives)

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
        val contractDifficulty = boardContract?.baseDifficulty ?: BalanceSettings.DEFAULT_CONTRACT_DIFFICULTY

        // Decision: Combined outcome + theft resolution
        val resolution = ResolutionModel.computeResolution(
            hero = hero,
            boardContract = boardContract,
            contractDifficulty = contractDifficulty,
            rng = rng
        )

        // Emit theft event if occurred
        if (resolution.theftOccurred && heroId != null) {
            ctx.emit(
                TrophyTheftSuspected(
                    day = newDay,
                    revision = workingState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    activeContractId = active.id.value,
                    heroId = heroId.value,
                    expectedTrophies = resolution.expectedTrophiesCount,
                    reportedTrophies = resolution.trophiesCount
                )
            )
        }

        // Track success/fail for stability
        val stabilityContrib = ResolutionModel.computeStabilityContribution(
            resolution.outcome,
            resolution.requiresPlayerClose
        )
        successfulReturns += stabilityContrib.successCount
        failedReturns += stabilityContrib.failCount

        // Emit ContractResolved event
        ctx.emit(
            ContractResolved(
                day = newDay,
                revision = workingState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                activeContractId = active.id.value,
                outcome = resolution.outcome,
                trophiesCount = resolution.trophiesCount,
                quality = resolution.trophiesQuality,
                reasonTags = intArrayOf()
            )
        )

        // Create return packet
        newReturns.add(
            ReturnPacket(
                activeContractId = active.id,
                boardContractId = active.boardContractId,
                heroIds = active.heroIds,
                resolvedDay = newDay,
                outcome = resolution.outcome,
                trophiesCount = resolution.trophiesCount,
                trophiesQuality = resolution.trophiesQuality,
                reasonTags = emptyList(),
                requiresPlayerClose = resolution.requiresPlayerClose,
                suspectedTheft = resolution.theftOccurred
            )
        )

        // Branch: player-close vs auto-close
        if (resolution.requiresPlayerClose) {
            val idx = updatedActives.indexOfFirst { it.id.value == activeId }
            if (idx >= 0) {
                updatedActives[idx] = updatedActives[idx].copy(daysRemaining = 0, status = ActiveStatus.RETURN_READY)
            }
        } else {
            // Auto-close path: apply all settlements immediately
            val economyDelta = EconomySettlement.computeAutoCloseDelta(
                resolution.outcome,
                boardContract,
                resolution.trophiesCount,
                workingState.economy
            )

            val heroResult = HeroLifecycle.computePostResolution(
                active.heroIds,
                resolution.outcome,
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
                workingState.contracts.board.map { b ->
                    if (b.id == boardContract.id) b.copy(status = BoardStatus.COMPLETED) else b
                }
            } else workingState.contracts.board

            // Settlement: Guild progression
            val guildResult = GuildProgression.computeAfterCompletion(
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

            // Keep arrivalsToday consistent
            val rosterIdSet = heroResult.updatedRoster.map { it.id }.toHashSet()
            val safeArrivalsToday = heroResult.updatedArrivalsToday.filter { it in rosterIdSet }

            // Apply all settlements
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
 * Phase 6: Stability Update
 *
 * Why:
 * - Stability is the public health meter of the region.
 * - It is the long-term difficulty driver.
 *
 * How:
 * - Applies a single clamp-bounded delta and emits a single event when changed.
 *
 * RNG: 0 draws (deterministic)
 */
private fun phaseStabilityUpdate(
    state: GameState,
    cmd: AdvanceDay,
    ctx: SeqContext,
    newDay: Int,
    successfulReturns: Int,
    failedReturns: Int
): GameState {
    val update = StabilityModel.computeFromResults(
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
 * Phase 7: Tax Evaluation
 *
 * Why:
 * - Taxes are the primary growth limiter.
 * - Misses must accumulate into an unambiguous shutdown risk.
 *
 * How:
 * - Evaluates due-day after the day pipeline.
 * - Applies penalty and miss counter updates in one place.
 *
 * RNG: 0 draws (deterministic)
 */
private fun phaseTax(
    state: GameState,
    cmd: AdvanceDay,
    ctx: SeqContext,
    newDay: Int
): GameState {
    val eval = TaxPolicy.evaluateEndOfDay(
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
        TaxEvaluationType.MISSED -> {
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
        TaxEvaluationType.DUE_SCHEDULED -> {
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

/**
 * Phase 8: Day Ended Snapshot
 *
 * Why:
 * - DayEnded is a stable integration point for analytics and UI.
 *
 * How:
 * - Captures a snapshot from the authoritative state without extra computation paths.
 *
 * RNG: 0 draws (deterministic)
 */
private fun phaseDayEndedAndSnapshot(
    state: GameState,
    cmd: AdvanceDay,
    ctx: SeqContext,
    newDay: Int
): GameState {
    val snapshot = SnapshotModel.createDaySnapshot(state, newDay)

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
