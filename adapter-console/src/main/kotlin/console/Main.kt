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
                state = applyAndPrint(state, cmd, rng)
            }

            "post" -> {
                if (parts.size < 3) continue
                val inboxId = parts[1].toLongOrNull() ?: continue
                val fee = parts[2].toIntOrNull() ?: continue

                val cmd = PostContract(
                    inboxId = inboxId,
                    fee = fee,
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

            "auto" -> {
                if (parts.size < 2) continue
                val n = parts[1].toIntOrNull() ?: continue
                if (n < 0) continue

                repeat(n) {
                    val cmd = AdvanceDay(cmdId = nextCmdId++)
                    state = applyAndPrint(state, cmd, rng)
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

private fun printHelp() {
    println(
        """
Commands:
  help
  status
  list inbox|board|active|returns
  day | advance
  post <inboxId> <fee>
  close <activeId>
  sell <amount>
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
    }
