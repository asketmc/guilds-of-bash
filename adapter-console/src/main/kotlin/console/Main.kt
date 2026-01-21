package console

import core.*
import core.hash.hashEvents
import core.hash.hashState
import core.primitives.ActiveStatus
import core.primitives.Outcome
import core.primitives.Quality
import core.rng.Rng
import core.state.GameState
import core.state.initialState

private const val STATE_SEED: UInt = 42u
private const val RNG_SEED: Long = 100L

fun main() {
    var state: GameState = initialState(STATE_SEED)
    val rng = Rng(RNG_SEED)
    var nextCmdId = 1L
    var prevDaySnapshot: DaySnapshot? = null

    println("Console adapter ready")
    println("stateSeed=$STATE_SEED rngSeed=$RNG_SEED")
    printHelp()

    while (true) {
        print("> ")
        val line = readLine() ?: break
        val trimmed = line.trim()
        if (trimmed.isEmpty()) continue

        val parts = trimmed.split(Regex("\\s+"))
        val cmdName = parts[0].lowercase()

        when (cmdName) {
            "help", "h", "?" -> printHelp()

            "quit", "q", "exit" -> return

            "status" -> printStatus(state, rng)

            "list" -> {
                if (parts.size < 2) continue
                when (parts[1].lowercase()) {
                    "inbox" -> printInbox(state)
                    "board" -> printBoard(state)
                    "active" -> printActive(state)
                    "returns", "return" -> printReturns(state)
                    else -> println("unknown command: $trimmed")
                }
            }

            "day", "advance" -> {
                val cmd = AdvanceDay(cmdId = nextCmdId++)
                val (newState, newSnapshot) = applyAndPrintWithAnalytics(state, cmd, rng, prevDaySnapshot)
                state = newState
                prevDaySnapshot = newSnapshot
            }

            "post" -> {
                if (parts.size < 4) {
                    println("Usage: post <inboxId> <fee> <salvage>")
                    println("  salvage: GUILD | HERO | SPLIT")
                    continue
                }
                val inboxId = parts[1].toLongOrNull() ?: continue
                val fee = parts[2].toIntOrNull() ?: continue
                val salvageStr = parts[3].uppercase()
                val salvage = try {
                    core.primitives.SalvagePolicy.valueOf(salvageStr)
                } catch (e: IllegalArgumentException) {
                    println("Invalid salvage policy: $salvageStr. Use GUILD, HERO, or SPLIT")
                    continue
                }

                val cmd = PostContract(
                    inboxId = inboxId,
                    fee = fee,
                    salvage = salvage,
                    cmdId = nextCmdId++
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "close" -> {
                if (parts.size < 2) continue
                val activeId = parts[1].toLongOrNull() ?: continue

                val cmd = CloseReturn(
                    activeContractId = activeId,
                    cmdId = nextCmdId++
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "sell" -> {
                val amount = if (parts.size == 1) {
                    0
                } else {
                    parts[1].toIntOrNull() ?: continue
                }
                val cmd = SellTrophies(
                    amount = amount,
                    cmdId = nextCmdId++
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "tax" -> {
                if (parts.size >= 3 && parts[1].lowercase() == "pay") {
                    val amount = parts[2].toIntOrNull() ?: continue
                    val cmd = PayTax(amount = amount, cmdId = nextCmdId++)
                    state = applyAndPrint(state, cmd, rng)
                } else {
                    println("Usage: tax pay <amount>")
                }
            }

            "create" -> {
                if (parts.size < 5) {
                    println("Usage: create <title> <rank> <difficulty> <reward> <salvage>")
                    println("  rank: F|E|D|C|B|A|S")
                    println("  difficulty: 0-100")
                    println("  salvage: GUILD|HERO|SPLIT")
                    continue
                }
                val title = parts[1]
                val rankStr = parts[2].uppercase()
                val difficulty = parts[3].toIntOrNull() ?: continue
                val reward = parts[4].toIntOrNull() ?: continue
                val salvageStr = parts.getOrNull(5)?.uppercase() ?: "GUILD"

                val rank = try {
                    core.primitives.Rank.valueOf(rankStr)
                } catch (e: IllegalArgumentException) {
                    println("Invalid rank: $rankStr. Use F, E, D, C, B, A, or S")
                    continue
                }

                val salvage = try {
                    core.primitives.SalvagePolicy.valueOf(salvageStr)
                } catch (e: IllegalArgumentException) {
                    println("Invalid salvage policy: $salvageStr. Use GUILD, HERO, or SPLIT")
                    continue
                }

                val cmd = CreateContract(
                    title = title,
                    rank = rank,
                    difficulty = difficulty,
                    reward = reward,
                    salvage = salvage,
                    cmdId = nextCmdId++
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "update" -> {
                if (parts.size < 2) {
                    println("Usage: update <contractId> [fee=<fee>] [salvage=<salvage>]")
                    println("  Example: update 5 fee=100")
                    println("  Example: update 5 salvage=HERO")
                    println("  Example: update 5 fee=80 salvage=SPLIT")
                    continue
                }
                val contractId = parts[1].toLongOrNull() ?: continue
                var newFee: Int? = null
                var newSalvage: core.primitives.SalvagePolicy? = null

                for (i in 2 until parts.size) {
                    val param = parts[i]
                    when {
                        param.startsWith("fee=") -> {
                            newFee = param.substringAfter("fee=").toIntOrNull()
                        }
                        param.startsWith("salvage=") -> {
                            val salvageStr = param.substringAfter("salvage=").uppercase()
                            newSalvage = try {
                                core.primitives.SalvagePolicy.valueOf(salvageStr)
                            } catch (e: IllegalArgumentException) {
                                println("Invalid salvage policy: $salvageStr")
                                continue
                            }
                        }
                    }
                }

                if (newFee == null && newSalvage == null) {
                    println("Must specify at least one parameter: fee=<value> or salvage=<policy>")
                    continue
                }

                val cmd = UpdateContractTerms(
                    contractId = contractId,
                    newFee = newFee,
                    newSalvage = newSalvage,
                    cmdId = nextCmdId++
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "cancel" -> {
                if (parts.size < 2) {
                    println("Usage: cancel <contractId>")
                    continue
                }
                val contractId = parts[1].toLongOrNull() ?: continue

                val cmd = CancelContract(
                    contractId = contractId,
                    cmdId = nextCmdId++
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "auto" -> {
                if (parts.size < 2) continue
                val n = parts[1].toIntOrNull() ?: continue
                if (n < 0) continue

                repeat(n) {
                    val cmd = AdvanceDay(cmdId = nextCmdId++)
                    val (newState, newSnapshot) = applyAndPrintWithAnalytics(state, cmd, rng, prevDaySnapshot)
                    state = newState
                    prevDaySnapshot = newSnapshot
                }
            }

            else -> println("unknown command: $trimmed")
        }
    }
}

private fun applyAndPrint(state: GameState, cmd: Command, rng: Rng): GameState {
    val result = step(state, cmd, rng)
    val newState = result.state
    val events = result.events

    println("CMD: ${cmd::class.simpleName} cmdId=${cmd.cmdId}")
    for (e in events) println(formatEvent(e))

    val stateHash = hashState(newState)
    val eventsHash = hashEvents(events)
    println("HASH: state=$stateHash events=$eventsHash rngDraws=${rng.draws}")
    println()

    return newState
}

/**
 * Apply command and print with day analytics (S7-S9) if DayEnded event is present.
 * Returns (newState, currentSnapshot) for tracking across days.
 */
private fun applyAndPrintWithAnalytics(
    state: GameState,
    cmd: Command,
    rng: Rng,
    prevSnapshot: DaySnapshot?
): Pair<GameState, DaySnapshot?> {
    val result = step(state, cmd, rng)
    val newState = result.state
    val events = result.events

    println("CMD: ${cmd::class.simpleName} cmdId=${cmd.cmdId}")
    for (e in events) println(formatEvent(e))

    // Check if DayEnded event is present to compute day analytics
    val dayEndedEvent = events.filterIsInstance<DayEnded>().firstOrNull()
    if (dayEndedEvent != null) {
        val currentSnapshot = events.filterIsInstance<DayEnded>().first().snapshot
        printDayAnalytics(events, currentSnapshot, prevSnapshot)

        val stateHash = hashState(newState)
        val eventsHash = hashEvents(events)
        println("HASH: state=$stateHash events=$eventsHash rngDraws=${rng.draws}")
        println()

        return newState to currentSnapshot
    }

    val stateHash = hashState(newState)
    val eventsHash = hashEvents(events)
    println("HASH: state=$stateHash events=$eventsHash rngDraws=${rng.draws}")
    println()

    return newState to prevSnapshot
}

/**
 * Compute and print day analytics: S7 (ContractTakeRate), S8 (OutcomeCounts), S9 (MoneyΔ).
 */
private fun printDayAnalytics(events: List<Event>, currentSnapshot: DaySnapshot, prevSnapshot: DaySnapshot?) {
    // S7: ContractTakeRate = ContractTaken.count / ContractPosted.count
    val contractsPosted = events.filterIsInstance<ContractPosted>().size
    val contractsTaken = events.filterIsInstance<ContractTaken>().size
    val takeRate = if (contractsPosted > 0) {
        "$contractsTaken/$contractsPosted"
    } else {
        "N/A (no posts)"
    }

    // S8: OutcomeCounts = aggregate ContractResolved.outcome
    val outcomeEvents = events.filterIsInstance<ContractResolved>()
    val outcomeCounts = outcomeEvents.groupingBy { it.outcome }.eachCount()
    val outcomeStr = if (outcomeCounts.isEmpty()) {
        "N/A (no resolutions)"
    } else {
        outcomeCounts.entries.joinToString(", ") { "${it.key}=${it.value}" }
    }

    // S9: MoneyΔ = current.money - prev.money
    val moneyDelta = if (prevSnapshot != null) {
        val delta = currentSnapshot.money - prevSnapshot.money
        val sign = if (delta >= 0) "+" else ""
        "$sign$delta"
    } else {
        "N/A (first day)"
    }

    println("─── Day Analytics ───")
    println("S7 ContractTakeRate: $takeRate")
    println("S8 OutcomeCounts: $outcomeStr")
    println("S9 MoneyΔDay: $moneyDelta")
    println("─────────────────────")
}

private fun printHelp() {
    println(
        """
Commands:
  help
  status
  list inbox|board|active|returns
  day | advance
  post <inboxId> <fee> <salvage>    (salvage: GUILD|HERO|SPLIT)
  create <title> <rank> <difficulty> <reward> <salvage>
  update <contractId> [fee=<fee>] [salvage=<salvage>]
  cancel <contractId>
  close <activeId>
  sell <amount>
  tax pay <amount>
  auto <n>
  quit
""".trimIndent()
    )
}

private fun printStatus(state: GameState, rng: Rng) {
    val returnsNeedingClose = state.contracts.returns.count { it.requiresPlayerClose }
    val activeWipCount = state.contracts.active.count { it.status == ActiveStatus.WIP }
    val availableCopper = state.economy.moneyCopper - state.economy.reservedCopper
    println("day=${state.meta.dayIndex} revision=${state.meta.revision} rngDraws=${rng.draws}")
    println("money=${state.economy.moneyCopper} reserved=${state.economy.reservedCopper} available=${availableCopper} trophies=${state.economy.trophiesStock}")
    println("stability=${state.region.stability} reputation=${state.guild.reputation} rank=${state.guild.guildRank}")
    println("tax: nextDue=${state.meta.taxDueDay} amountDue=${state.meta.taxAmountDue} penalty=${state.meta.taxPenalty} missed=${state.meta.taxMissedCount}")
    println("counts: inbox=${state.contracts.inbox.size} board=${state.contracts.board.size} active=${activeWipCount} returnsNeedingClose=${returnsNeedingClose}")
    println("hash=${hashState(state)}")
}

private fun printInbox(state: GameState) {
    val xs = state.contracts.inbox.sortedBy { it.id.value }
    if (xs.isEmpty()) return println("(inbox empty)")
    for (d in xs) {
        println("inboxId=${d.id.value} day=${d.createdDay} rank=${d.rankSuggested} feeOffered=${d.feeOffered} title=\"${d.title}\"")
    }
}

private fun printBoard(state: GameState) {
    val xs = state.contracts.board.sortedBy { it.id.value }
    if (xs.isEmpty()) return println("(board empty)")
    for (b in xs) {
        println("boardId=${b.id.value} status=${b.status} rank=${b.rank} fee=${b.fee} salvage=${b.salvage} title=\"${b.title}\"")
    }
}

private fun printActive(state: GameState) {
    val xs = state.contracts.active.sortedBy { it.id.value }
    if (xs.isEmpty()) return println("(active empty)")
    for (a in xs) {
        val heroes = a.heroIds.map { it.value }.sorted()
        println("activeId=${a.id.value} boardId=${a.boardContractId.value} status=${a.status} daysRemaining=${a.daysRemaining} heroes=$heroes")
    }
}

private fun printReturns(state: GameState) {
    val xs = state.contracts.returns.sortedBy { it.activeContractId.value }
    if (xs.isEmpty()) return println("(returns empty)")
    for (r in xs) {
        println("activeId=${r.activeContractId.value} resolvedDay=${r.resolvedDay} outcome=${r.outcome} trophies=${r.trophiesCount} quality=${r.trophiesQuality} requiresClose=${r.requiresPlayerClose}")
    }
}

private fun formatEvent(e: Event): String =
    when (e) {
        is DayStarted -> "E#${e.seq} DayStarted day=${e.day} rev=${e.revision} cmdId=${e.cmdId}"
        is InboxGenerated -> "E#${e.seq} InboxGenerated day=${e.day} rev=${e.revision} cmdId=${e.cmdId} count=${e.count} ids=${e.contractIds.contentToString()}"
        is HeroesArrived -> "E#${e.seq} HeroesArrived day=${e.day} rev=${e.revision} cmdId=${e.cmdId} count=${e.count} ids=${e.heroIds.contentToString()}"
        is ContractPosted -> "E#${e.seq} ContractPosted day=${e.day} rev=${e.revision} cmdId=${e.cmdId} boardId=${e.boardContractId} fromInbox=${e.fromInboxId} rank=${e.rank} fee=${e.fee} salvage=${e.salvage}"
        is ContractTaken -> "E#${e.seq} ContractTaken day=${e.day} rev=${e.revision} cmdId=${e.cmdId} activeId=${e.activeContractId} boardId=${e.boardContractId} heroes=${e.heroIds.contentToString()} daysRemaining=${e.daysRemaining}"
        is WipAdvanced -> "E#${e.seq} WipAdvanced day=${e.day} rev=${e.revision} cmdId=${e.cmdId} activeId=${e.activeContractId} daysRemaining=${e.daysRemaining}"
        is ContractResolved -> "E#${e.seq} ContractResolved day=${e.day} rev=${e.revision} cmdId=${e.cmdId} activeId=${e.activeContractId} outcome=${e.outcome} trophies=${e.trophiesCount} quality=${e.quality}"
        is ReturnClosed -> "E#${e.seq} ReturnClosed day=${e.day} rev=${e.revision} cmdId=${e.cmdId} activeId=${e.activeContractId}"
        is TrophySold -> "E#${e.seq} TrophySold day=${e.day} rev=${e.revision} cmdId=${e.cmdId} amount=${e.amount} earned=${e.moneyGained}"
        is StabilityUpdated -> "E#${e.seq} StabilityUpdated day=${e.day} rev=${e.revision} cmdId=${e.cmdId} stability=${e.oldStability}->${e.newStability}"
        is DayEnded -> "E#${e.seq} DayEnded day=${e.day} rev=${e.revision} cmdId=${e.cmdId}"
        is CommandRejected -> "E#${e.seq} CommandRejected day=${e.day} rev=${e.revision} cmdId=${e.cmdId} cmdType=${e.cmdType} reason=${e.reason} detail=${e.detail}"
        is InvariantViolated -> "E#${e.seq} InvariantViolated day=${e.day} rev=${e.revision} cmdId=${e.cmdId} invariant=${e.invariantId.code} details=${e.details}"
        is HeroDeclined -> "E#${e.seq} HeroDeclined day=${e.day} rev=${e.revision} cmdId=${e.cmdId} heroId=${e.heroId} boardId=${e.boardContractId} reason=${e.reason}"
        is TrophyTheftSuspected -> "E#${e.seq} TrophyTheftSuspected day=${e.day} rev=${e.revision} cmdId=${e.cmdId} activeId=${e.activeContractId} heroId=${e.heroId} expected=${e.expectedTrophies} reported=${e.reportedTrophies}"
        is TaxPaid -> "E#${e.seq} TaxPaid day=${e.day} rev=${e.revision} cmdId=${e.cmdId} amountPaid=${e.amountPaid} amountDue=${e.amountDue} partial=${e.isPartialPayment}"
        is TaxMissed -> "E#${e.seq} TaxMissed day=${e.day} rev=${e.revision} cmdId=${e.cmdId} amountDue=${e.amountDue} penaltyAdded=${e.penaltyAdded} missed=${e.missedCount}"
        is TaxDue -> "E#${e.seq} TaxDue day=${e.day} rev=${e.revision} cmdId=${e.cmdId} amountDue=${e.amountDue} dueDay=${e.dueDay}"
        is GuildRankUp -> "E#${e.seq} GuildRankUp day=${e.day} rev=${e.revision} cmdId=${e.cmdId} oldRank=${e.oldRank} newRank=${e.newRank} completed=${e.completedContracts}"
        is GuildShutdown -> "E#${e.seq} GuildShutdown day=${e.day} rev=${e.revision} cmdId=${e.cmdId} reason=${e.reason}"
        is ContractDraftCreated -> "E#${e.seq} ContractDraftCreated day=${e.day} rev=${e.revision} cmdId=${e.cmdId} draftId=${e.draftId} title=\"${e.title}\" rank=${e.rank} difficulty=${e.difficulty} reward=${e.reward} salvage=${e.salvage}"
        is ContractTermsUpdated -> "E#${e.seq} ContractTermsUpdated day=${e.day} rev=${e.revision} cmdId=${e.cmdId} contractId=${e.contractId} location=${e.location} oldFee=${e.oldFee} newFee=${e.newFee} oldSalvage=${e.oldSalvage} newSalvage=${e.newSalvage}"
        is ContractCancelled -> "E#${e.seq} ContractCancelled day=${e.day} rev=${e.revision} cmdId=${e.cmdId} contractId=${e.contractId} location=${e.location} refundedCopper=${e.refundedCopper}"
        is ProofPolicyChanged -> "E#${e.seq} ProofPolicyChanged day=${e.day} rev=${e.revision} cmdId=${e.cmdId} oldPolicy=${e.oldPolicy} newPolicy=${e.newPolicy}"
        else -> "E#0 UnknownEvent"
    }
