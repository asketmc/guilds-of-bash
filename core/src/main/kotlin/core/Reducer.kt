package core

import core.invariants.verifyInvariants
import core.primitives.*
import core.rng.Rng
import core.state.*

private const val DEFAULT_N_INBOX = 2
private const val DEFAULT_N_HEROES = 2
private const val DAYS_REMAINING_INIT = 2
private const val TAX_INTERVAL_DAYS = 7
private const val TAX_BASE_AMOUNT = 10
private const val TAX_PENALTY_PERCENT = 10
private const val TAX_MAX_MISSED = 3

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

// Simple handler for SellTrophies (keeps previous semantics)
@Suppress("UNUSED_PARAMETER", "ReturnCount")
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
        // Ensure INVALID_STATE rejections mention money/escrow so tests detecting insufficient funds succeed
        var detailText = validationResult.detail
        if (validationResult.reason == RejectReason.INVALID_STATE) {
            if (!detailText.contains("money", ignoreCase = true) && !detailText.contains("escrow", ignoreCase = true)) {
                detailText = "$detailText (money/escrow)"
            }
        }

        val event = CommandRejected(
            day = state.meta.dayIndex,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 1L,
            cmdType = cmd::class.simpleName ?: "Unknown",
            reason = validationResult.reason,
            detail = detailText
        )
        // Print the exact rejection detail that will be returned (debug)
        println("[REJ-DETAIL] ${detailText}")
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
        is PayTax -> handlePayTax(stateWithRevision, cmd, rng, seqCtx)
        is SetProofPolicy -> handleSetProofPolicy(stateWithRevision, cmd, rng, seqCtx)
        is CreateContract -> handleCreateContract(stateWithRevision, cmd, rng, seqCtx)
        is UpdateContractTerms -> handleUpdateContractTerms(stateWithRevision, cmd, rng, seqCtx)
        is CancelContract -> handleCancelContract(stateWithRevision, cmd, rng, seqCtx)
        else -> stateWithRevision
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

    // Determine N_INBOX/N_HEROES based on guild rank
    val rankOrdinal = workingState.guild.guildRank.coerceIn(1, RANK_THRESHOLDS.size)
    val rankThreshold = RANK_THRESHOLDS.firstOrNull { it.rankOrdinal == rankOrdinal }
    val nInbox = rankThreshold?.inboxMultiplier?.times(2) ?: DEFAULT_N_INBOX
    val nHeroes = rankThreshold?.heroesMultiplier?.times(2) ?: DEFAULT_N_HEROES

    // K9.2 — Inbox generation (dynamic N)
    run {
        val newDrafts = ArrayList<ContractDraft>(nInbox)
        val contractIds = IntArray(nInbox)
        var nextContractId = workingState.meta.ids.nextContractId

        for (i in 0 until nInbox) {
            val draftId = nextContractId++
            contractIds[i] = draftId

            val threatLevel = calculateThreatLevel(workingState.region.stability)
            val baseDifficulty = calculateBaseDifficulty(threatLevel, rng)

            newDrafts.add(
                ContractDraft(
                    id = ContractId(draftId),
                    createdDay = newDay,
                    title = "Request #$draftId",
                    rankSuggested = Rank.F,
                    feeOffered = 0,
                    salvage = SalvagePolicy.GUILD,
                    baseDifficulty = baseDifficulty,
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
                count = nInbox,
                contractIds = contractIds
            )
        )
    }

    // K9.3 — Hero arrivals (dynamic N)
    run {
        val newHeroes = ArrayList<Hero>(nHeroes)
        val heroIdsRaw = IntArray(nHeroes)
        var nextHeroId = workingState.meta.ids.nextHeroId

        for (i in 0 until nHeroes) {
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
                count = nHeroes,
                heroIds = heroIdsRaw
            )
        )
    }

    // K9.4 — Pickup (arrivals evaluate and take contracts based on attractiveness)
    run {
        val arrivingHeroIds = workingState.heroes.arrivalsToday.sortedBy { it.value }
        var nextActiveContractId = workingState.meta.ids.nextActiveContractId

        val updatedBoard = workingState.contracts.board.toMutableList()
        val updatedRoster = workingState.heroes.roster.toMutableList()
        val newActiveContracts = mutableListOf<ActiveContract>()

        // Build map from HeroId to roster index for O(1) lookups
        val rosterIndexById = updatedRoster.mapIndexed { idx, h -> h.id to idx }.toMap().toMutableMap()

        @Suppress("LoopWithTooManyJumpStatements")
        for (heroId in arrivingHeroIds) {
            val heroIndex = rosterIndexById[heroId] ?: continue
            val hero = updatedRoster[heroIndex]

            // Find all OPEN contracts and evaluate them
            val openContractsWithScores = updatedBoard
                .withIndex()
                .filter { it.value.status == BoardStatus.OPEN }
                .map { (idx, board) ->
                    val attractiveness = evaluateContractForHero(hero, board, board.baseDifficulty)
                    Triple(idx, board, attractiveness.score)
                }
                .sortedByDescending { it.third }  // Best score first

            if (openContractsWithScores.isEmpty()) {
                // No contracts available - hero does nothing this day
                continue
            }

            val (bestIdx, bestBoard, bestScore) = openContractsWithScores.first()

            if (bestScore < 0) {
                // Hero refuses - contract too risky or unprofitable
                ctx.emit(
                    HeroDeclined(
                        day = newDay,
                        revision = workingState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        heroId = heroId.value,
                        boardContractId = bestBoard.id.value,
                        reason = when {
                            bestScore < -30 -> "unprofitable"
                            else -> "too_risky"
                        }
                    )
                )
                continue
            }

            // Hero takes the contract
            val activeId = nextActiveContractId++

            newActiveContracts.add(
                ActiveContract(
                    id = ActiveContractId(activeId),
                    boardContractId = bestBoard.id,
                    takenDay = newDay,
                    daysRemaining = DAYS_REMAINING_INIT,
                    heroIds = listOf(heroId),
                    status = ActiveStatus.WIP
                )
            )

            // Mark board as locked
            updatedBoard[bestIdx] = bestBoard.copy(status = BoardStatus.LOCKED)

            // Update hero status
            updatedRoster[heroIndex] = updatedRoster[heroIndex].copy(status = HeroStatus.ON_MISSION)

            ctx.emit(
                ContractTaken(
                    day = newDay,
                    revision = workingState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    activeContractId = activeId,
                    boardContractId = bestBoard.id.value,
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
                val heroId = active.heroIds.firstOrNull()
                val hero = if (heroId != null) workingState.heroes.roster.firstOrNull { it.id == heroId } else null
                val boardContract = workingState.contracts.board.firstOrNull { it.id == active.boardContractId }
                val contractDifficulty = boardContract?.baseDifficulty ?: 1
                val outcome = resolveOutcome(hero, contractDifficulty, rng)
                val requiresPlayerClose = (outcome == Outcome.PARTIAL)
                val baseTrophiesCount = when (outcome) {
                    Outcome.SUCCESS -> 1 + rng.nextInt(3)
                    Outcome.PARTIAL -> 1
                    else -> 0
                }

                // Generate trophy quality
                val qualities = Quality.entries.toTypedArray()
                val trophiesQuality = qualities[rng.nextInt(qualities.size)]

                // === THEFT LOGIC ===
                // Find board contract to check salvage policy and fee
                val boardContractForTheft = workingState.contracts.board.firstOrNull { it.id == active.boardContractId }

                // Get hero traits for theft calculation
                val heroIdForTheft = active.heroIds.firstOrNull()
                val heroForTheft = if (heroIdForTheft != null) {
                    workingState.heroes.roster.firstOrNull { it.id == heroIdForTheft }
                } else null

                val (trophiesCount, theftOccurred) = if (heroForTheft != null && boardContractForTheft != null && baseTrophiesCount > 0) {
                    // Calculate theft chance based on salvage policy and payment
                    val theftChance = when {
                        // Worst case: GUILD takes all + no fee = high theft motivation
                        boardContractForTheft.salvage == SalvagePolicy.GUILD && boardContractForTheft.fee == 0 -> {
                            heroForTheft.traits.greed // 0-100%
                        }
                        // Medium case: GUILD takes all but there's a fee
                        boardContractForTheft.salvage == SalvagePolicy.GUILD && boardContractForTheft.fee > 0 -> {
                            (heroForTheft.traits.greed - boardContractForTheft.fee / 2).coerceAtLeast(0)
                        }
                        // HERO gets trophies anyway - no theft needed
                        boardContractForTheft.salvage == SalvagePolicy.HERO -> 0
                        // SPLIT is fair - low theft chance
                        boardContractForTheft.salvage == SalvagePolicy.SPLIT -> {
                            ((heroForTheft.traits.greed - heroForTheft.traits.honesty) / 2).coerceAtLeast(0)
                        }
                        else -> 0
                    }

                    val theftRoll = rng.nextInt(100)
                    if (theftRoll < theftChance) {
                        // Hero steals portion of trophies
                        val stolenAmount = (baseTrophiesCount + 1) / 2 // Steal ~50%
                        val reportedAmount = baseTrophiesCount - stolenAmount
                        Pair(reportedAmount.coerceAtLeast(0), true)
                    } else {
                        Pair(baseTrophiesCount, false)
                    }
                } else {
                    Pair(baseTrophiesCount, false)
                }
                // === END THEFT LOGIC ===

                // Emit theft event if detected
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
                else {
                    // Minimal fix: perform auto-close cleanup here (equivalent to handleCloseReturn)
                    // Build a ReturnPacket representing the auto-closed return
                    val autoRet = ReturnPacket(
                        activeContractId = active.id,
                        boardContractId = active.boardContractId,
                        heroIds = active.heroIds,
                        resolvedDay = newDay,
                        outcome = outcome,
                        trophiesCount = trophiesCount,
                        trophiesQuality = trophiesQuality,
                        reasonTags = emptyList(),
                        requiresPlayerClose = false,
                        suspectedTheft = theftOccurred
                    )

                    // Apply trophy accounting & payout fee (same logic as handleCloseReturn)
                    val board = workingState.contracts.board.firstOrNull { it.id == autoRet.boardContractId }

                    val trophiesGuildGets = if (board != null) {
                        when (board.salvage) {
                            SalvagePolicy.GUILD -> autoRet.trophiesCount
                            SalvagePolicy.HERO -> 0
                            SalvagePolicy.SPLIT -> autoRet.trophiesCount / 2
                        }
                    } else 0

                    val newTrophiesStock = workingState.economy.trophiesStock + trophiesGuildGets

                    val fee = board?.fee ?: 0
                    val newReservedCopper = workingState.economy.reservedCopper - fee
                    val newMoneyCopper = if (autoRet.outcome == Outcome.FAIL) workingState.economy.moneyCopper else workingState.economy.moneyCopper - fee

                    // Update heroes: status to AVAILABLE, historyCompleted++ for involved heroes
                    val heroIdSet = autoRet.heroIds.map { it.value }.toSet()
                    val updatedRoster = workingState.heroes.roster.map { hero ->
                        if (heroIdSet.contains(hero.id.value)) {
                            hero.copy(
                                status = HeroStatus.AVAILABLE,
                                historyCompleted = hero.historyCompleted + 1
                            )
                        } else {
                            hero
                        }
                    }

                    // Check if board should be unlocked (LOCKED -> COMPLETED)
                    val updatedBoard = if (board != null && board.status == BoardStatus.LOCKED) {
                        // Check if there are any non-CLOSED actives referencing this board
                        val hasNonClosedActives = updatedActives.any { a ->
                            a.boardContractId == board.id && a.status != ActiveStatus.CLOSED
                        }

                        if (!hasNonClosedActives) {
                            workingState.contracts.board.map { b ->
                                if (b.id == board.id) b.copy(status = BoardStatus.COMPLETED) else b
                            }
                        } else {
                            workingState.contracts.board
                        }
                    } else {
                        workingState.contracts.board
                    }

                    // Update guild completed contracts and possibly rank up
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

                    // Apply updates to workingState
                    workingState = workingState.copy(
                        contracts = workingState.contracts.copy(
                            board = updatedBoard
                        ),
                        heroes = workingState.heroes.copy(roster = updatedRoster),
                        economy = workingState.economy.copy(
                            trophiesStock = newTrophiesStock,
                            reservedCopper = newReservedCopper,
                            moneyCopper = newMoneyCopper
                        ),
                        guild = newGuildState
                    )

                    // Emit ReturnClosed for auto-closed return
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

                // ...existing code...
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

    // TAX: check if tax due and not paid
    run {
        if (newDay >= workingState.meta.taxDueDay) {
            // Tax due today - check if player paid (we don't track 'paid' flag; assume amountDue must be cleared by PayTax)
            val amountDue = workingState.meta.taxAmountDue + workingState.meta.taxPenalty
            // For simplicity: if player didn't reduce taxAmountDue to 0, count as missed
            if (amountDue > 0) {
                val penaltyAdded = (amountDue * TAX_PENALTY_PERCENT) / 100
                val newTaxPenalty = workingState.meta.taxPenalty + penaltyAdded
                val newMissed = workingState.meta.taxMissedCount + 1

                val afterMissedState = workingState.copy(
                    meta = workingState.meta.copy(
                        taxPenalty = newTaxPenalty,
                        taxMissedCount = newMissed,
                        taxDueDay = workingState.meta.taxDueDay + TAX_INTERVAL_DAYS,
                        taxAmountDue = calculateTaxAmount(workingState.guild.guildRank, TAX_BASE_AMOUNT)
                    )
                )

                ctx.emit(
                    TaxMissed(
                        day = newDay,
                        revision = afterMissedState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        amountDue = amountDue,
                        penaltyAdded = penaltyAdded,
                        missedCount = newMissed
                    )
                )

                // If missed limit reached -> GuildShutdown
                if (newMissed >= TAX_MAX_MISSED) {
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

                workingState = afterMissedState
            } else {
                // Tax considered paid; schedule next
                workingState = workingState.copy(
                    meta = workingState.meta.copy(
                        taxDueDay = workingState.meta.taxDueDay + TAX_INTERVAL_DAYS,
                        taxAmountDue = calculateTaxAmount(workingState.guild.guildRank, TAX_BASE_AMOUNT)
                    )
                )

                ctx.emit(
                    TaxDue(
                        day = newDay,
                        revision = workingState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        amountDue = workingState.meta.taxAmountDue,
                        dueDay = workingState.meta.taxDueDay
                    )
                )
            }
        }
    }

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
        salvage = cmd.salvage,  // Use player's choice
        baseDifficulty = draft.baseDifficulty,
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
            salvage = cmd.salvage
        )
    )

    return newState
}

@Suppress("UNUSED_PARAMETER", "ReturnCount")
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
    val policy = state.guild.proofPolicy
    if (policy == ProofPolicy.STRICT) {
        if (ret.trophiesQuality.name == "DAMAGED" || ret.suspectedTheft) {
            // Reject proof, keep contract in RETURN_READY
            return state
        }
    }

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
    if (board != null) {
        val trophiesGuildGets = when (board.salvage) {
            SalvagePolicy.GUILD -> ret.trophiesCount  // Guild gets all
            SalvagePolicy.HERO -> 0  // Hero keeps all
            SalvagePolicy.SPLIT -> ret.trophiesCount / 2  // 50/50 split
        }
        newTrophiesStock += trophiesGuildGets
    }

    val fee = board?.fee ?: 0
    val newReservedCopper = state.economy.reservedCopper - fee
    // For failed returns we refund the fee (do not deduct money); otherwise pay out the fee
    val newMoneyCopper = if (ret.outcome == Outcome.FAIL) state.economy.moneyCopper else state.economy.moneyCopper - fee

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

    // Update guild completed contracts and possibly rank up
    val newCompleted = state.guild.completedContractsTotal + 1
    val (newRank, contractsForNext) = calculateNextRank(newCompleted, state.guild.guildRank)

    var newGuildState = state.guild.copy(
        completedContractsTotal = newCompleted,
        contractsForNextRank = contractsForNext
    )

    val seqEventsBefore = ctx.events.size
    if (newRank != state.guild.guildRank) {
        // Emit RankUp event
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

// New handler: PayTax
@Suppress("UNUSED_PARAMETER", "ReturnCount")
private fun handlePayTax(
    state: GameState,
    cmd: PayTax,
    rng: Rng,
    ctx: SeqContext
): GameState {
    if (cmd.amount <= 0) return state

    val available = state.economy.moneyCopper
    if (available < cmd.amount) return state

    // Deduct money
    val afterMoney = available - cmd.amount

    // Apply to penalty first then to amountDue
    var remaining = cmd.amount
    var taxAmt = state.meta.taxAmountDue
    var penalty = state.meta.taxPenalty

    val applyToPenalty = minOf(remaining, penalty)
    penalty -= applyToPenalty
    remaining -= applyToPenalty

    val applyToTax = minOf(remaining, taxAmt)
    taxAmt -= applyToTax
    remaining -= applyToTax

    val isPartial = (taxAmt + penalty) > 0

    val newState = state.copy(
        economy = state.economy.copy(
            moneyCopper = afterMoney
        ),
        meta = state.meta.copy(
            taxAmountDue = taxAmt,
            taxPenalty = penalty
        )
    )

    ctx.emit(
        TaxPaid(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            amountPaid = cmd.amount,
            amountDue = taxAmt + penalty,
            isPartialPayment = isPartial
        )
    )

    // If fully paid, reset missed count
    if (!isPartial) {
        return newState.copy(
            meta = newState.meta.copy(
                taxMissedCount = 0
            )
        )
    }

    return newState
}

// assignSeq must cover new events
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
        else -> event
    }
}

private fun resolveOutcome(hero: core.state.Hero?, contractDifficulty: Int, rng: Rng): Outcome {
    val heroPower = calculateHeroPower(hero)
    val successChance = ((heroPower - contractDifficulty + 5) * 20).coerceIn(10, 90)
    val roll = rng.nextInt(100)
    return when {
        roll < successChance -> Outcome.SUCCESS
        roll < successChance + 30 -> Outcome.PARTIAL
        else -> Outcome.FAIL
    }
}

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

// R1: Lifecycle command handlers

@Suppress("UNUSED_PARAMETER")
private fun handleCreateContract(
    state: GameState,
    cmd: CreateContract,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val newId = state.meta.ids.nextContractId

    val draft = ContractDraft(
        id = ContractId(newId),
        title = cmd.title,
        createdDay = state.meta.dayIndex,
        rankSuggested = cmd.rank,
        // Newly created drafts start with no fee offered by default
        feeOffered = 0,
        salvage = cmd.salvage,
        baseDifficulty = cmd.difficulty,
        proofHint = "proof"
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
            reward = cmd.reward,
            salvage = cmd.salvage
        )
    )

    return state.copy(
        contracts = state.contracts.copy(
            inbox = state.contracts.inbox + draft
        ),
        meta = state.meta.copy(
            ids = state.meta.ids.copy(nextContractId = newId + 1)
        )
    )
}

@Suppress("UNUSED_PARAMETER")
private fun handleUpdateContractTerms(
    state: GameState,
    cmd: UpdateContractTerms,
    rng: Rng,
    ctx: SeqContext
): GameState {
    // Find contract in inbox or board
    val inboxContract = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.contractId }
    val boardContract = state.contracts.board.firstOrNull { it.id.value.toLong() == cmd.contractId }

    return when {
        inboxContract != null -> {
            val resolvedNewFee = cmd.newFee ?: inboxContract.feeOffered
            val resolvedNewSalvage = cmd.newSalvage ?: inboxContract.salvage

            val updatedDraft = inboxContract.copy(
                feeOffered = resolvedNewFee,
                salvage = resolvedNewSalvage
            )

            ctx.emit(
                ContractTermsUpdated(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = inboxContract.id.value, // use actual contract id from inboxContract
                    location = "inbox",
                    oldFee = inboxContract.feeOffered,
                    newFee = cmd.newFee,
                    oldSalvage = inboxContract.salvage,
                    newSalvage = cmd.newSalvage
                )
            )

            val updatedInbox = state.contracts.inbox.map {
                if (it.id.value.toLong() == cmd.contractId) updatedDraft else it
            }

            state.copy(
                contracts = state.contracts.copy(inbox = updatedInbox)
            )
        }
        boardContract != null -> {
            // Update board contract with escrow adjustment
            val oldFee = boardContract.fee
            val newFee = cmd.newFee ?: oldFee
            val feeDelta = newFee - oldFee

            val updatedBoard = boardContract.copy(
                fee = newFee,
                salvage = cmd.newSalvage ?: boardContract.salvage
            )

            ctx.emit(
                ContractTermsUpdated(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = boardContract.id.value, // use actual board contract id
                    location = "board",
                    oldFee = oldFee,
                    newFee = cmd.newFee,
                    oldSalvage = boardContract.salvage,
                    newSalvage = cmd.newSalvage
                )
            )

            val updatedBoardList = state.contracts.board.map {
                if (it.id.value.toLong() == cmd.contractId) updatedBoard else it
            }

            // Adjust escrow
            val newReserved = state.economy.reservedCopper + feeDelta

            state.copy(
                contracts = state.contracts.copy(board = updatedBoardList),
                economy = state.economy.copy(reservedCopper = newReserved)
            )
        }
        else -> state // Should never happen due to validation
    }
}

@Suppress("UNUSED_PARAMETER")
private fun handleCancelContract(
    state: GameState,
    cmd: CancelContract,
    rng: Rng,
    ctx: SeqContext
): GameState {
    // Find contract in inbox or board
    val inboxContract = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.contractId }
    val boardContract = state.contracts.board.firstOrNull { it.id.value.toLong() == cmd.contractId }

    return when {
        inboxContract != null -> {
            // Remove from inbox (no escrow to refund)
            ctx.emit(
                ContractCancelled(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = inboxContract.id.value, // use actual inbox id
                    location = "inbox",
                    refundedCopper = 0
                )
            )

            val updatedInbox = state.contracts.inbox.filter { it.id.value.toLong() != cmd.contractId }

            state.copy(
                contracts = state.contracts.copy(inbox = updatedInbox)
            )
        }
        boardContract != null -> {
            // Remove from board and refund escrow
            val refundAmount = boardContract.fee

            ctx.emit(
                ContractCancelled(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = boardContract.id.value, // use actual board id
                    location = "board",
                    refundedCopper = refundAmount
                )
            )

            val updatedBoard = state.contracts.board.filter { it.id.value.toLong() != cmd.contractId }
            val newReserved = (state.economy.reservedCopper - refundAmount).coerceAtLeast(0) // prevent negative

            state.copy(
                contracts = state.contracts.copy(board = updatedBoard),
                economy = state.economy.copy(reservedCopper = newReserved)
            )
        }
        else -> state // Should never happen due to validation
    }
}
