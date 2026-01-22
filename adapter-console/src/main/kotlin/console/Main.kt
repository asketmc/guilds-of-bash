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
            "help", "h", "?" -> {
                printCmdInput(trimmed, state, rng)
                printCmdVars()
                printHelp()
            }

            "quit", "q", "exit" -> {
                printCmdInput(trimmed, state, rng)
                printCmdVars()
                return
            }

            "status" -> {
                printCmdInput(trimmed, state, rng)
                printCmdVars()
                printStatus(state, rng)
            }

            "list" -> {
                printCmdInput(trimmed, state, rng)
                if (parts.size < 2) {
                    printCmdVars("error" to "missing target", "usage" to "list inbox|board|active|returns")
                    println("Usage: list inbox|board|active|returns")
                    continue
                }
                val target = parts[1].lowercase()
                printCmdVars("target" to target)
                when (target) {
                    "inbox" -> printInbox(state)
                    "board" -> printBoard(state)
                    "active" -> printActive(state)
                    "returns", "return" -> printReturns(state)
                    else -> unknownCommand(trimmed)
                }
            }

            "day", "advance" -> {
                printCmdInput(trimmed, state, rng)
                val cmdId = nextCmdId++
                printCmdVars("cmdId" to cmdId)
                val cmd = AdvanceDay(cmdId = cmdId)
                val (newState, newSnapshot) = applyAndPrintWithAnalytics(state, cmd, rng, prevDaySnapshot)
                state = newState
                prevDaySnapshot = newSnapshot
            }

            "post" -> {
                printCmdInput(trimmed, state, rng)
                if (parts.size < 4) {
                    printCmdVars("error" to "missing args", "usage" to "post <inboxId> <fee> <salvage>")
                    println("Usage: post <inboxId> <fee> <salvage>")
                    println("  salvage: GUILD | HERO | SPLIT")
                    continue
                }

                val inboxIdRaw = parts[1]
                val feeRaw = parts[2]
                val salvageStr = parts[3].uppercase()

                val inboxId = inboxIdRaw.toLongOrNull()
                if (inboxId == null) {
                    printCmdVars("error" to "invalid inboxId", "inboxIdRaw" to inboxIdRaw)
                    println("Invalid inboxId: $inboxIdRaw")
                    continue
                }

                val fee = feeRaw.toIntOrNull()
                if (fee == null) {
                    printCmdVars("error" to "invalid fee", "feeRaw" to feeRaw)
                    println("Invalid fee: $feeRaw")
                    continue
                }

                val salvage = try {
                    core.primitives.SalvagePolicy.valueOf(salvageStr)
                } catch (e: IllegalArgumentException) {
                    printCmdVars("error" to "invalid salvage", "salvageRaw" to salvageStr)
                    println("Invalid salvage policy: $salvageStr. Use GUILD, HERO, or SPLIT")
                    continue
                }

                val cmdId = nextCmdId++
                printCmdVars(
                    "cmdId" to cmdId,
                    "inboxId" to inboxId,
                    "fee" to fee,
                    "salvage" to salvage
                )

                val cmd = PostContract(
                    inboxId = inboxId,
                    fee = fee,
                    salvage = salvage,
                    cmdId = cmdId
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "close" -> {
                printCmdInput(trimmed, state, rng)
                if (parts.size < 2) {
                    printCmdVars("error" to "missing activeId", "usage" to "close <activeId>")
                    println("Usage: close <activeId>")
                    continue
                }

                val activeIdRaw = parts[1]
                val activeId = activeIdRaw.toLongOrNull()
                if (activeId == null) {
                    printCmdVars("error" to "invalid activeId", "activeIdRaw" to activeIdRaw)
                    println("Invalid activeId: $activeIdRaw")
                    continue
                }

                val cmdId = nextCmdId++
                printCmdVars("cmdId" to cmdId, "activeId" to activeId)

                val cmd = CloseReturn(
                    activeContractId = activeId,
                    cmdId = cmdId
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "sell" -> {
                printCmdInput(trimmed, state, rng)

                val amount = if (parts.size == 1) {
                    0
                } else {
                    val raw = parts[1]
                    val parsed = raw.toIntOrNull()
                    if (parsed == null) {
                        printCmdVars("error" to "invalid amount", "amountRaw" to raw, "usage" to "sell <amount>")
                        println("Invalid amount: $raw")
                        continue
                    }
                    parsed
                }

                val cmdId = nextCmdId++
                printCmdVars("cmdId" to cmdId, "amount" to amount)

                val cmd = SellTrophies(
                    amount = amount,
                    cmdId = cmdId
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "tax" -> {
                printCmdInput(trimmed, state, rng)
                if (parts.size >= 3 && parts[1].lowercase() == "pay") {
                    val amountRaw = parts[2]
                    val amount = amountRaw.toIntOrNull()
                    if (amount == null) {
                        printCmdVars("error" to "invalid amount", "amountRaw" to amountRaw, "usage" to "tax pay <amount>")
                        println("Invalid amount: $amountRaw")
                        continue
                    }
                    val cmdId = nextCmdId++
                    printCmdVars("cmdId" to cmdId, "amount" to amount)
                    val cmd = PayTax(amount = amount, cmdId = cmdId)
                    state = applyAndPrint(state, cmd, rng)
                } else {
                    printCmdVars("error" to "invalid subcommand", "usage" to "tax pay <amount>")
                    println("Usage: tax pay <amount>")
                }
            }

            "create" -> {
                printCmdInput(trimmed, state, rng)
                if (parts.size < 5) {
                    printCmdVars("error" to "missing args", "usage" to "create <title> <rank> <difficulty> <reward> [salvage]")
                    println("Usage: create <title> <rank> <difficulty> <reward> [salvage]")
                    println("  rank: F|E|D|C|B|A|S")
                    println("  difficulty: 0-100")
                    println("  salvage: GUILD|HERO|SPLIT")
                    continue
                }

                val title = parts[1]
                val rankStr = parts[2].uppercase()

                val difficultyRaw = parts[3]
                val rewardRaw = parts[4]
                val salvageStr = parts.getOrNull(5)?.uppercase() ?: "GUILD"

                val difficulty = difficultyRaw.toIntOrNull()
                if (difficulty == null) {
                    printCmdVars("error" to "invalid difficulty", "difficultyRaw" to difficultyRaw)
                    println("Invalid difficulty: $difficultyRaw")
                    continue
                }

                val reward = rewardRaw.toIntOrNull()
                if (reward == null) {
                    printCmdVars("error" to "invalid reward", "rewardRaw" to rewardRaw)
                    println("Invalid reward: $rewardRaw")
                    continue
                }

                val rank = try {
                    core.primitives.Rank.valueOf(rankStr)
                } catch (e: IllegalArgumentException) {
                    printCmdVars("error" to "invalid rank", "rankRaw" to rankStr)
                    println("Invalid rank: $rankStr. Use F, E, D, C, B, A, or S")
                    continue
                }

                val salvage = try {
                    core.primitives.SalvagePolicy.valueOf(salvageStr)
                } catch (e: IllegalArgumentException) {
                    printCmdVars("error" to "invalid salvage", "salvageRaw" to salvageStr)
                    println("Invalid salvage policy: $salvageStr. Use GUILD, HERO, or SPLIT")
                    continue
                }

                val cmdId = nextCmdId++
                printCmdVars(
                    "cmdId" to cmdId,
                    "title" to title,
                    "rank" to rank,
                    "difficulty" to difficulty,
                    "reward" to reward,
                    "salvage" to salvage
                )

                val cmd = CreateContract(
                    title = title,
                    rank = rank,
                    difficulty = difficulty,
                    reward = reward,
                    salvage = salvage,
                    cmdId = cmdId
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "update" -> {
                printCmdInput(trimmed, state, rng)
                if (parts.size < 2) {
                    printCmdVars("error" to "missing contractId", "usage" to "update <contractId> [fee=<fee>] [salvage=<salvage>]")
                    println("Usage: update <contractId> [fee=<fee>] [salvage=<salvage>]")
                    println("  Example: update 5 fee=100")
                    println("  Example: update 5 salvage=HERO")
                    println("  Example: update 5 fee=80 salvage=SPLIT")
                    continue
                }

                val contractIdRaw = parts[1]
                val contractId = contractIdRaw.toLongOrNull()
                if (contractId == null) {
                    printCmdVars("error" to "invalid contractId", "contractIdRaw" to contractIdRaw)
                    println("Invalid contractId: $contractIdRaw")
                    continue
                }

                var newFee: Int? = null
                var newSalvage: core.primitives.SalvagePolicy? = null

                for (i in 2 until parts.size) {
                    val param = parts[i]
                    when {
                        param.startsWith("fee=") -> {
                            val raw = param.substringAfter("fee=")
                            val parsed = raw.toIntOrNull()
                            if (parsed == null) {
                                println("Invalid fee: $raw")
                            } else {
                                newFee = parsed
                            }
                        }
                        param.startsWith("salvage=") -> {
                            val salvageRaw = param.substringAfter("salvage=").uppercase()
                            newSalvage = try {
                                core.primitives.SalvagePolicy.valueOf(salvageRaw)
                            } catch (e: IllegalArgumentException) {
                                println("Invalid salvage policy: $salvageRaw")
                                null
                            }
                        }
                    }
                }

                if (newFee == null && newSalvage == null) {
                    printCmdVars("contractId" to contractId, "newFee" to newFee, "newSalvage" to newSalvage, "error" to "no params")
                    println("Must specify at least one parameter: fee=<value> or salvage=<policy>")
                    continue
                }

                val cmdId = nextCmdId++
                printCmdVars("cmdId" to cmdId, "contractId" to contractId, "newFee" to newFee, "newSalvage" to newSalvage)

                val cmd = UpdateContractTerms(
                    contractId = contractId,
                    newFee = newFee,
                    newSalvage = newSalvage,
                    cmdId = cmdId
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "cancel" -> {
                printCmdInput(trimmed, state, rng)
                if (parts.size < 2) {
                    printCmdVars("error" to "missing contractId", "usage" to "cancel <contractId>")
                    println("Usage: cancel <contractId>")
                    continue
                }

                val contractIdRaw = parts[1]
                val contractId = contractIdRaw.toLongOrNull()
                if (contractId == null) {
                    printCmdVars("error" to "invalid contractId", "contractIdRaw" to contractIdRaw)
                    println("Invalid contractId: $contractIdRaw")
                    continue
                }

                val cmdId = nextCmdId++
                printCmdVars("cmdId" to cmdId, "contractId" to contractId)

                val cmd = CancelContract(
                    contractId = contractId,
                    cmdId = cmdId
                )
                state = applyAndPrint(state, cmd, rng)
            }

            "auto" -> {
                printCmdInput(trimmed, state, rng)
                if (parts.size < 2) {
                    printCmdVars("error" to "missing n", "usage" to "auto <n>")
                    println("Usage: auto <n>")
                    continue
                }

                val nRaw = parts[1]
                val n = nRaw.toIntOrNull()
                if (n == null) {
                    printCmdVars("error" to "invalid n", "nRaw" to nRaw)
                    println("Invalid n: $nRaw")
                    continue
                }
                if (n < 0) {
                    printCmdVars("error" to "n<0", "n" to n)
                    println("Invalid n: $n (must be >= 0)")
                    continue
                }

                printCmdVars("count" to n)

                repeat(n) {
                    val cmdId = nextCmdId++
                    println("AUTO_STEP: index=${it + 1}/$n")
                    printCmdVars("cmdId" to cmdId)
                    val cmd = AdvanceDay(cmdId = cmdId)
                    val (newState, newSnapshot) = applyAndPrintWithAnalytics(state, cmd, rng, prevDaySnapshot)
                    state = newState
                    prevDaySnapshot = newSnapshot
                }
            }

            else -> unknownCommand(trimmed)
        }
    }
}

private fun unknownCommand(input: String) {
    println("Неизвестная команда: $input")
    println("Введите 'help' для списка команд.")
}

private fun printCmdInput(input: String, state: GameState, rng: Rng) {
    println("IN: \"$input\"")
    println("CTX: day=${state.meta.dayIndex} rev=${state.meta.revision} rngDraws=${rng.draws}")
}

private fun printCmdVars(vararg kv: Pair<String, Any?>) {
    if (kv.isEmpty()) {
        println("VARS: (none)")
        return
    }
    println("VARS: " + kv.joinToString(" ") { "${it.first}=${it.second}" })
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
  create <title> <rank> <difficulty> <reward> [salvage]
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
