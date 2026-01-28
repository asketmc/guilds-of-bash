// FILE: core/src/main/kotlin/core/Reducer.kt
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

private const val DEBUG_REJECTIONS = false

data class StepResult(
    val state: GameState,
    val events: List<Event>
)

/**
 * Context for building events with sequential seq numbers.
 */
class SeqContext {
    val events = ArrayList<Event>(32)
    private var nextSeq = 1L

    fun emit(event: Event): Event {
        val withSeq = assignSeq(event, nextSeq++)
        events.add(withSeq)
        return withSeq
    }
}

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
 * Pure reducer: (state, command, rng) -> (newState, events)
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

        val event = CommandRejected(
            day = state.meta.dayIndex,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 1L,
            cmdType = cmd::class.simpleName ?: "Unknown",
            reason = validationResult.reason,
            detail = detailText
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
        val lastEventIsDayEnded = seqCtx.events.lastOrNull() is DayEnded
        val insertIndex = if (lastEventIsDayEnded) seqCtx.events.size - 1 else seqCtx.events.size

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
        seqCtx.events.addAll(insertIndex, violationEvents)

        for (i in seqCtx.events.indices) {
            seqCtx.events[i] = assignSeq(seqCtx.events[i], (i + 1).toLong())
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
    val newDay = state.meta.dayIndex + 1
    var workingState = state.copy(
        meta = state.meta.copy(dayIndex = newDay),
        heroes = state.heroes.copy(arrivalsToday = emptyList())
    )

    ctx.emit(DayStarted(day = newDay, revision = workingState.meta.revision, cmdId = cmd.cmdId, seq = 0L))

    val rankOrdinal = workingState.guild.guildRank.coerceIn(1, RANK_THRESHOLDS.size)
    val rankThreshold = RANK_THRESHOLDS.firstOrNull { it.rankOrdinal == rankOrdinal }
    val nInbox = rankThreshold?.inboxMultiplier?.times(2) ?: DEFAULT_N_INBOX
    val nHeroes = rankThreshold?.heroesMultiplier?.times(2) ?: DEFAULT_N_HEROES

    // K9.2 — Inbox generation
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
                    nextAutoResolveDay = newDay + 7,
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
            contracts = workingState.contracts.copy(inbox = workingState.contracts.inbox + newDrafts),
            meta = workingState.meta.copy(ids = workingState.meta.ids.copy(nextContractId = nextContractId))
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

    // K9.3 — Hero arrivals
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

        val arrivalsToday = List(nHeroes) { HeroId(heroIdsRaw[it]) }

        workingState = workingState.copy(
            heroes = workingState.heroes.copy(
                roster = workingState.heroes.roster + newHeroes,
                arrivalsToday = arrivalsToday
            ),
            meta = workingState.meta.copy(ids = workingState.meta.ids.copy(nextHeroId = nextHeroId))
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

    // K9.3.5 — Contract expiry auto-resolve (inbox only, weekly cadence)
    run {
        val dueDrafts = workingState.contracts.inbox.filter { it.nextAutoResolveDay <= newDay }

        if (dueDrafts.isNotEmpty()) {
            val updatedInbox = workingState.contracts.inbox.toMutableList()
            var cumulativeStabilityDelta = 0

            for (draft in dueDrafts) {
                val bucketRoll = rng.nextInt(3)
                val bucket = when (bucketRoll) {
                    0 -> AutoResolveBucket.GOOD
                    1 -> AutoResolveBucket.NEUTRAL
                    2 -> AutoResolveBucket.BAD
                    else -> AutoResolveBucket.GOOD
                }

                ctx.emit(
                    ContractAutoResolved(
                        day = newDay,
                        revision = workingState.meta.revision,
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
                            updatedInbox[idx] = draft.copy(nextAutoResolveDay = newDay + 7)
                        }
                    }
                    AutoResolveBucket.BAD -> {
                        updatedInbox.removeIf { it.id == draft.id }
                        cumulativeStabilityDelta -= 2
                    }
                }
            }

            workingState = workingState.copy(
                contracts = workingState.contracts.copy(inbox = updatedInbox)
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
        }
    }

    // K9.4 — Pickup (optimized: scan)
    run {
        val arrivingHeroIds = workingState.heroes.arrivalsToday.sortedBy { it.value }
        var nextActiveContractId = workingState.meta.ids.nextActiveContractId

        val board = workingState.contracts.board.toMutableList()
        val roster = workingState.heroes.roster.toMutableList()
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
                        revision = workingState.meta.revision,
                        cmdId = cmd.cmdId,
                        seq = 0L,
                        heroId = heroId.value,
                        boardContractId = chosen.id.value,
                        reason = if (bestScore < -30) "unprofitable" else "too_risky"
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
                    daysRemaining = DAYS_REMAINING_INIT,
                    heroIds = listOf(heroId),
                    status = ActiveStatus.WIP
                )
            )

            board[bestBoardIndex] = chosen.copy(status = BoardStatus.LOCKED)
            roster[heroIndex] = hero.copy(status = HeroStatus.ON_MISSION)

            ctx.emit(
                ContractTaken(
                    day = newDay,
                    revision = workingState.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    activeContractId = activeId,
                    boardContractId = chosen.id.value,
                    heroIds = intArrayOf(heroId.value),
                    daysRemaining = DAYS_REMAINING_INIT
                )
            )
        }

        workingState = workingState.copy(
            contracts = workingState.contracts.copy(board = board, active = workingState.contracts.active + newActives),
            heroes = workingState.heroes.copy(roster = roster),
            meta = workingState.meta.copy(ids = workingState.meta.ids.copy(nextActiveContractId = nextActiveContractId))
        )
    }

    // K9.5 — WIP advance + resolve (REVERTED to original semantics to restore P1_002)
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

                // Generate trophy quality (original)
                val qualities = Quality.entries.toTypedArray()
                val trophiesQuality = qualities[rng.nextInt(qualities.size)]

                // THEFT LOGIC (original)
                val boardContractForTheft = workingState.contracts.board.firstOrNull { it.id == active.boardContractId }
                val heroIdForTheft = active.heroIds.firstOrNull()
                val heroForTheft = if (heroIdForTheft != null) {
                    workingState.heroes.roster.firstOrNull { it.id == heroIdForTheft }
                } else null

                val (trophiesCount, theftOccurred) =
                    if (heroForTheft != null && boardContractForTheft != null && baseTrophiesCount > 0) {
                        val theftChance = when {
                            boardContractForTheft.salvage == SalvagePolicy.GUILD && boardContractForTheft.fee == 0 ->
                                heroForTheft.traits.greed
                            boardContractForTheft.salvage == SalvagePolicy.GUILD && boardContractForTheft.fee > 0 ->
                                (heroForTheft.traits.greed - boardContractForTheft.fee / 2).coerceAtLeast(0)
                            boardContractForTheft.salvage == SalvagePolicy.HERO -> 0
                            boardContractForTheft.salvage == SalvagePolicy.SPLIT ->
                                ((heroForTheft.traits.greed - heroForTheft.traits.honesty) / 2).coerceAtLeast(0)
                            else -> 0
                        }

                        val theftRoll = rng.nextInt(100)
                        if (theftRoll < theftChance) {
                            val stolenAmount = (baseTrophiesCount + 1) / 2
                            val reportedAmount = baseTrophiesCount - stolenAmount
                            Pair(reportedAmount.coerceAtLeast(0), true)
                        } else {
                            Pair(baseTrophiesCount, false)
                        }
                    } else {
                        Pair(baseTrophiesCount, false)
                    }

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
                    // Auto-close cleanup (original semantics)
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
                    val newMoneyCopper =
                        if (autoRet.outcome == Outcome.FAIL) workingState.economy.moneyCopper
                        else workingState.economy.moneyCopper - fee

                    val heroIdSet = autoRet.heroIds.map { it.value }.toSet()
                    val updatedRoster = workingState.heroes.roster.map { h ->
                        if (heroIdSet.contains(h.id.value)) {
                            h.copy(status = HeroStatus.AVAILABLE, historyCompleted = h.historyCompleted + 1)
                        } else h
                    }

                    val updatedBoard = if (board != null && board.status == BoardStatus.LOCKED) {
                        val hasNonClosedActives = updatedActives.any { a ->
                            a.boardContractId == board.id && a.status != ActiveStatus.CLOSED
                        }
                        if (!hasNonClosedActives) {
                            workingState.contracts.board.map { b ->
                                if (b.id == board.id) b.copy(status = BoardStatus.COMPLETED) else b
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
                        heroes = workingState.heroes.copy(roster = updatedRoster),
                        economy = workingState.economy.copy(
                            trophiesStock = newTrophiesStock,
                            reservedCopper = newReservedCopper,
                            moneyCopper = newMoneyCopper
                        ),
                        guild = newGuildState
                    )

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
    }

    // Stability update (before DayEnded)
    run {
        val oldStability = workingState.region.stability
        val delta = successfulReturns - failedReturns
        val newStability = (oldStability + delta).coerceIn(0, 100)

        if (newStability != oldStability) {
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
    }

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
            val amountDue = workingState.meta.taxAmountDue + workingState.meta.taxPenalty
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
        salvage = cmd.salvage,
        baseDifficulty = draft.baseDifficulty,
        status = BoardStatus.OPEN
    )

    val newState = state.copy(
        contracts = state.contracts.copy(
            inbox = state.contracts.inbox.filter { it.id.value.toLong() != cmd.inboxId },
            board = state.contracts.board + boardContract
        ),
        economy = state.economy.copy(reservedCopper = state.economy.reservedCopper + fee)
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
    val ret = state.contracts.returns.firstOrNull { it.activeContractId.value.toLong() == cmd.activeContractId } ?: return state
    val board = state.contracts.board.firstOrNull { it.id == ret.boardContractId }

    val policy = state.guild.proofPolicy
    if (policy == ProofPolicy.STRICT) {
        if (ret.trophiesQuality.name == "DAMAGED" || ret.suspectedTheft) return state
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

    val fee = board?.fee ?: 0
    val newReservedCopper = state.economy.reservedCopper - fee
    val newMoneyCopper = if (ret.outcome == Outcome.FAIL) state.economy.moneyCopper else state.economy.moneyCopper - fee

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

    val afterMoney = available - cmd.amount

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
        economy = state.economy.copy(moneyCopper = afterMoney),
        meta = state.meta.copy(taxAmountDue = taxAmt, taxPenalty = penalty)
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

    return if (!isPartial) newState.copy(meta = newState.meta.copy(taxMissedCount = 0)) else newState
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
        createdDay = state.meta.dayIndex,
        nextAutoResolveDay = state.meta.dayIndex + 7,
        title = cmd.title,
        rankSuggested = cmd.rank,
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
        contracts = state.contracts.copy(inbox = state.contracts.inbox + draft),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextContractId = newId + 1))
    )
}

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
            val resolvedNewFee = cmd.newFee ?: inboxContract.feeOffered
            val resolvedNewSalvage = cmd.newSalvage ?: inboxContract.salvage

            val updatedDraft = inboxContract.copy(feeOffered = resolvedNewFee, salvage = resolvedNewSalvage)

            ctx.emit(
                ContractTermsUpdated(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = inboxContract.id.value,
                    location = "inbox",
                    oldFee = inboxContract.feeOffered,
                    newFee = cmd.newFee,
                    oldSalvage = inboxContract.salvage,
                    newSalvage = cmd.newSalvage
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
                    contractId = boardContract.id.value,
                    location = "board",
                    oldFee = oldFee,
                    newFee = cmd.newFee,
                    oldSalvage = boardContract.salvage,
                    newSalvage = cmd.newSalvage
                )
            )

            state.copy(
                contracts = state.contracts.copy(
                    board = state.contracts.board.map { if (it.id.value.toLong() == cmd.contractId) updatedBoard else it }
                ),
                economy = state.economy.copy(reservedCopper = state.economy.reservedCopper + feeDelta)
            )
        }

        else -> state
    }
}

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
            val refundAmount = boardContract.fee

            ctx.emit(
                ContractCancelled(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = boardContract.id.value,
                    location = "board",
                    refundedCopper = refundAmount
                )
            )

            state.copy(
                contracts = state.contracts.copy(board = state.contracts.board.filter { it.id.value.toLong() != cmd.contractId }),
                economy = state.economy.copy(reservedCopper = (state.economy.reservedCopper - refundAmount).coerceAtLeast(0))
            )
        }

        else -> state
    }
}
