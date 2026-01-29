/**
 * Console adapter for the deterministic core.
 *
 * Why this exists:
 * - Provide a minimal, dependency-free harness to exercise core commands interactively without adding UI complexity.
 * - Make determinism observable: every command prints the emitted event stream plus stable hashes of state/events and RNG draw count.
 * - Support long-term regression detection: hashes and printed event order make it easy to spot drift after refactors.
 * - Support manual repro: fixed seeds and monotonic `cmdId` allow copy/paste session transcripts to reproduce behavior.
 *
 * This file is intentionally *not* a source of truth for game rules.
 * All authority is in `core.step(state, cmd, rng)`; this adapter only translates typed user input into commands and prints outputs.
 */
package console

import core.*
import core.hash.hashEvents
import core.hash.hashState
import core.primitives.ActiveStatus
import core.primitives.BoardStatus
import core.primitives.HeroClass
import core.primitives.Outcome
import core.rng.Rng
import core.state.GameState
import core.state.initialState

/**
 * Fixed initial-state seed for reproducible interactive sessions.
 *
 * Why fixed:
 * - Ensures that a saved transcript of console inputs replays the same initial world shape.
 * - Prevents accidental reliance on "current randomness" when diagnosing bugs.
 */
private const val STATE_SEED: UInt = 42u

/**
 * Fixed RNG seed for reproducible command execution.
 *
 * Why fixed:
 * - Makes the RNG draw sequence comparable across runs and across machines.
 * - Enables hashing-based regression checks that depend on deterministic event/state evolution.
 */
private const val RNG_SEED: Long = 100L

/**
 * Interactive REPL for issuing core commands and printing observable outputs.
 *
 * Why this loop is structured as-is:
 * - The adapter prints *input context* (day, revision, RNG draws) before action to support log-based debugging.
 * - It prints *events* after each command because events are the canonical "telemetry" surface of the deterministic core.
 * - It prints *hashes* after each command to provide a stable fingerprint for golden-replay style comparisons.
 * - It maintains a monotonic `cmdId` locally to preserve a stable audit trail across a session.
 */
fun main() {
    var state: GameState = initialState(STATE_SEED)
    val rng = Rng(RNG_SEED)
    var nextCmdId = 1L
    var prevDaySnapshot: DaySnapshot? = null
    var gazetteBuffer = GazetteBuffer()

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
                // Also print diegetic version
                DiegeticHelp.render().forEach { println(it) }
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
                // Also print diegetic version
                DiegeticStatus.render(state, rng).forEach { println(it) }
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
                    "inbox" -> {
                        printInbox(state)
                        // Also print framed version with flavor
                        ContractListRenderer.renderInbox(state).forEach { println(it) }
                    }
                    "board" -> {
                        printBoard(state)
                        // Also print framed version with flavor
                        ContractListRenderer.renderBoard(state).forEach { println(it) }
                    }
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
                val (newState, newSnapshot, newGazetteBuffer) = applyAndPrintWithAnalytics(
                    state, cmd, rng, prevDaySnapshot, gazetteBuffer
                )
                state = newState
                prevDaySnapshot = newSnapshot
                gazetteBuffer = newGazetteBuffer
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
                } catch (_: IllegalArgumentException) {
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
                } catch (_: IllegalArgumentException) {
                    printCmdVars("error" to "invalid rank", "rankRaw" to rankStr)
                    println("Invalid rank: $rankStr. Use F, E, D, C, B, A, or S")
                    continue
                }

                val salvage = try {
                    core.primitives.SalvagePolicy.valueOf(salvageStr)
                } catch (_: IllegalArgumentException) {
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
                            } catch (_: IllegalArgumentException) {
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
                    val (newState, newSnapshot, newGazetteBuffer) = applyAndPrintWithAnalytics(
                        state, cmd, rng, prevDaySnapshot, gazetteBuffer
                    )
                    state = newState
                    prevDaySnapshot = newSnapshot
                    gazetteBuffer = newGazetteBuffer
                }
            }

            else -> unknownCommand(trimmed)
        }
    }
}

/**
 * Keeps the REPL resilient to typos without throwing, which is important for manual exploration sessions.
 *
 * Why:
 * - Prevents accidental termination of the harness during long debugging runs.
 * - Encourages keeping console sessions as pasteable transcripts (invalid lines are still visible in logs).
 *
 * @param input Raw user input line.
 */
private fun unknownCommand(input: String) {
    println("Неизвестная команда: $input")
    println("Введите 'help' для списка команд.")
}

/**
 * Prints the command input and the minimal execution context required to reason about determinism.
 *
 * Why:
 * - `day` and `rev` explain which logical "time" the command ran in.
 * - `rngDraws` provides a cheap invariant-like signal: unintended extra draws frequently correlate with replay drift.
 *
 * @param input Raw user input as entered.
 * @param state Current state before applying the command.
 * @param rng RNG instance used by `step`, exposing draw count for diagnostics.
 */
private fun printCmdInput(input: String, state: GameState, rng: Rng) {
    println("IN: \"$input\"")
    println("CTX: day=${state.meta.dayIndex} rev=${state.meta.revision} rngDraws=${rng.draws}")
}

/**
 * Prints parsed command variables in a compact, greppable format.
 *
 * Why:
 * - Provides structured breadcrumbs in logs without needing a logging framework.
 * - Helps correlate a failing/rejected command to the exact parsed values (especially after copy/paste edits).
 *
 * @param kv Key/value pairs representing parsed variables or error context.
 */
private fun printCmdVars(vararg kv: Pair<String, Any?>) {
    if (kv.isEmpty()) {
        println("VARS: (none)")
        return
    }
    println("VARS: " + kv.joinToString(" ") { "${it.first}=${it.second}" })
}

/**
 * Applies a [Command] via [step] and prints the observable outputs (events + stable hashes).
 *
 * Why hashes are printed:
 * - They act as a durable "golden fingerprint" for a given command result.
 * - They enable cheap, copy/paste regression checks even when event lists are long.
 *
 * Why RNG draw count is printed:
 * - Draw count is an early warning for determinism contract violations (extra draws shift all downstream randomness).
 *
 * @param state State before command application.
 * @param cmd Command instance (already parsed and typed).
 * @param rng RNG instance shared across the session.
 * @return New state returned by [step].
 */
private fun applyAndPrint(state: GameState, cmd: Command, rng: Rng): GameState {
    val result = step(state, cmd, rng)
    val newState = result.state
    val events = result.events

    println("CMD: ${cmd::class.simpleName} cmdId=${cmd.cmdId}")
    for (e in events) println(formatEvent(e))

    // Print single-command flavor hook (for post, close, sell, tax pay)
    val flavourHook = renderCommandHook(state, newState, events)
    if (flavourHook != null) {
        println("─── Flavour ───")
        println(flavourHook)
        println("───────────────")
    }

    val stateHash = hashState(newState)
    val eventsHash = hashEvents(events)
    println("HASH: state=$stateHash events=$eventsHash rngDraws=${rng.draws}")
    println()

    return newState
}

/**
 * Applies a command and optionally prints cross-day analytics when a [DayEnded] event is present.
 *
 * Why this exists separately from [applyAndPrint]:
 * - Day-level metrics require a boundary signal; [DayEnded] provides a stable boundary without peeking into core internals.
 * - Cross-day deltas (e.g., money delta) require a previous snapshot; returning the current snapshot makes this explicit.
 *
 * The function returns `(newState, currentSnapshotOrPrevious, updatedGazetteBuffer)` to preserve the last known snapshot when the command
 * does not end a day, which keeps the REPL state machine simple and predictable.
 *
 * @param state State before command application.
 * @param cmd Command to apply.
 * @param rng RNG instance shared across the session.
 * @param prevSnapshot Previous day snapshot (nullable for first day or first boundary observed).
 * @param gazetteBuffer Buffer tracking last 7 day snapshots for gazette generation.
 * @return Triple of (new state, snapshot to carry forward, updated gazette buffer).
 */
private fun applyAndPrintWithAnalytics(
    state: GameState,
    cmd: Command,
    rng: Rng,
    prevSnapshot: DaySnapshot?,
    gazetteBuffer: GazetteBuffer
): Triple<GameState, DaySnapshot?, GazetteBuffer> {
    val prevState = state
    val result = step(state, cmd, rng)
    val newState = result.state
    val events = result.events

    println("CMD: ${cmd::class.simpleName} cmdId=${cmd.cmdId}")
    for (e in events) println(formatEvent(e))

    // Print hero quotes for contract resolutions (Feature 5)
    events.filterIsInstance<ContractResolved>().forEach { resolved ->
        val quote = HeroQuotes.forResolution(resolved, newState)
        println("─── Hero Quote ───")
        println(quote)
        println("──────────────────")
    }

    // Check if DayEnded event is present to compute day analytics
    val currentSnapshot = events.filterIsInstance<DayEnded>().firstOrNull()?.snapshot
    if (currentSnapshot != null) {
        printDayAnalytics(events, currentSnapshot, prevSnapshot)
        printDayReport(prevState = prevState, newState = newState, events = events)

        // Print day narrative flavour
        val narrativeLines = renderDayNarrative(prevState, newState, events)
        if (narrativeLines.isNotEmpty()) {
            println("─── Narrative ───")
            narrativeLines.forEach { println(it) }
            println("─────────────────")
        }

        // Update gazette buffer and potentially render gazette (Feature 1)
        val gazetteSnapshot = GazetteSnapshot.fromDaySnapshot(currentSnapshot)
        val updatedBuffer = gazetteBuffer.add(gazetteSnapshot)

        // Render weekly gazette if applicable
        val gazetteLines = GazetteRenderer.render(currentSnapshot.day, updatedBuffer, gazetteSnapshot)
        if (gazetteLines != null) {
            println()
            gazetteLines.forEach { println(it) }
        }

        val stateHash = hashState(newState)
        val eventsHash = hashEvents(events)
        println("HASH: state=$stateHash events=$eventsHash rngDraws=${rng.draws}")
        println()

        return Triple(newState, currentSnapshot, updatedBuffer)
    }

    val stateHash = hashState(newState)
    val eventsHash = hashEvents(events)
    println("HASH: state=$stateHash events=$eventsHash rngDraws=${rng.draws}")
    println()

    return Triple(newState, prevSnapshot, gazetteBuffer)
}

private fun printDayReport(prevState: GameState, newState: GameState, events: List<Event>) {
    // Contracts
    val inboxGenerated = events.filterIsInstance<InboxGenerated>().sumOf { it.count }
    val posted = events.count { it is ContractPosted }
    val cancelled = events.count { it is ContractCancelled }

    val boardCounts = newState.contracts.board
        .groupingBy { it.status }
        .eachCount()
        .withDefault { 0 }
    val boardOpen = boardCounts.getValue(BoardStatus.OPEN)
    val boardLocked = boardCounts.getValue(BoardStatus.LOCKED)
    val boardCompleted = boardCounts.getValue(BoardStatus.COMPLETED)

    val activeCounts = newState.contracts.active
        .groupingBy { it.status }
        .eachCount()
        .withDefault { 0 }
    val activeWip = activeCounts.getValue(ActiveStatus.WIP)
    val activeReturnReady = activeCounts.getValue(ActiveStatus.RETURN_READY)

    val returnsNeedingClose = newState.contracts.returns.count { it.requiresPlayerClose }

    // Heroes
    val rosterSize = newState.heroes.roster.size
    val arrivalsToday = events.filterIsInstance<HeroesArrived>().sumOf { it.count }

    val arrivalsSet = newState.heroes.arrivalsToday.map { it.value }.toHashSet()
    val arrivalsHeroes = newState.heroes.roster.filter { arrivalsSet.contains(it.id.value) }
    val classCountsRaw = arrivalsHeroes.groupingBy { it.klass }.eachCount()
    val classesToday = fmtEnumCounts(HeroClass.entries.toList(), classCountsRaw)

    // Resolutions
    val resolvedEvents = events.filterIsInstance<ContractResolved>()
    val resolved = resolvedEvents.size
    val outcomeCounts = resolvedEvents.groupingBy { it.outcome }.eachCount()
    val success = outcomeCounts[Outcome.SUCCESS] ?: 0
    val partial = outcomeCounts[Outcome.PARTIAL] ?: 0
    val fail = outcomeCounts[Outcome.FAIL] ?: 0
    val theftSuspected = events.count { it is TrophyTheftSuspected }

    // Economy
    val money = newState.economy.moneyCopper
    val moneyDelta = money - prevState.economy.moneyCopper
    val reserved = newState.economy.reservedCopper
    val reservedDelta = reserved - prevState.economy.reservedCopper
    val available = money - reserved
    val trophies = newState.economy.trophiesStock
    val trophiesDelta = trophies - prevState.economy.trophiesStock

    // Region/Guild
    val stability = newState.region.stability
    val stabilityDelta = stability - prevState.region.stability
    val rank = newState.guild.guildRank
    val completedTotal = newState.guild.completedContractsTotal
    val completedDelta = completedTotal - prevState.guild.completedContractsTotal

    val taxDueDay = newState.meta.taxDueDay
    val taxDue = newState.meta.taxAmountDue
    val taxPenalty = newState.meta.taxPenalty
    val taxMissed = newState.meta.taxMissedCount

    println("─── Day Report ───")
    println(
        "Contracts: inboxGenerated=+$inboxGenerated posted=+$posted cancelled=+$cancelled " +
            "board(open=$boardOpen locked=$boardLocked completed=$boardCompleted) " +
            "active(wip=$activeWip returnReady=$activeReturnReady) " +
            "returnsNeedingClose=$returnsNeedingClose"
    )
    println("Heroes: roster=$rosterSize arrivalsToday=+$arrivalsToday classesToday={$classesToday}")
    println(
        "Resolutions: resolved=+$resolved success=$success partial=$partial fail=$fail theftSuspected=$theftSuspected"
    )
    println(
        "Economy: money=$money ${fmtDelta(moneyDelta)} reserved=$reserved ${fmtDelta(reservedDelta)} " +
            "available=$available trophies=$trophies ${fmtDelta(trophiesDelta)}"
    )
    println(
        "Region/Guild: stability=$stability ${fmtDelta(stabilityDelta)} rank=$rank " +
            "completedContractsTotal=$completedTotal ${fmtDelta(completedDelta)} " +
            "tax(dueDay=$taxDueDay, due=$taxDue, penalty=$taxPenalty, missed=$taxMissed)"
    )
    println("──────────────────")
}

private fun fmtDelta(delta: Int): String = when {
    delta > 0 -> "(+${delta})"
    delta < 0 -> "(${delta})"
    else -> "(0)"
}

private fun <E : Enum<E>> fmtEnumCounts(all: List<E>, counts: Map<E, Int>): String {
    val ordered = all.sortedBy { it.name }
    val parts = ordered.mapNotNull { e ->
        val c = counts[e] ?: 0
        if (c == 0) null else "${e.name}=$c"
    }
    return if (parts.isEmpty()) "" else parts.joinToString(", ")
}

/**
 * Prints the command list.
 *
 * Why keep it local and static:
 * - Avoids reflection-based discovery that could introduce ordering instability or require extra dependencies.
 * - Makes the console a stable "tool" rather than a moving UI target while the core evolves.
 */
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

/**
 * Prints a concise, human-oriented snapshot of the current [GameState].
 *
 * Why:
 * - Designed for fast sanity checks during interactive sessions without scrolling through full lists.
 * - Mirrors invariant-critical quantities (available money, active/returns counts) to surface broken transitions early.
 *
 * @param state Current state to summarize.
 * @param rng RNG instance to expose current draw count alongside state summary.
 */
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

/**
 * Prints inbox drafts in a stable order.
 *
 * Why sorting matters:
 * - Keeps output diff-friendly across runs and across JVMs (even if underlying storage order changes later).
 * - Supports copy/paste debugging where humans expect "the same thing prints the same way".
 */
private fun printInbox(state: GameState) {
    val xs = state.contracts.inbox.sortedBy { it.id.value }
    if (xs.isEmpty()) return println("(inbox empty)")
    for (d in xs) {
        println("inboxId=${d.id.value} day=${d.createdDay} rank=${d.rankSuggested} feeOffered=${d.feeOffered} title=\"${d.title}\"")
    }
}

/**
 * Prints board contracts in a stable order.
 *
 * Why:
 * - Makes it easier to correlate board IDs with subsequent events and actives.
 * - Reduces noise in long sessions where board contents evolve.
 */
private fun printBoard(state: GameState) {
    val xs = state.contracts.board.sortedBy { it.id.value }
    if (xs.isEmpty()) return println("(board empty)")
    for (b in xs) {
        println("boardId=${b.id.value} status=${b.status} rank=${b.rank} fee=${b.fee} salvage=${b.salvage} title=\"${b.title}\"")
    }
}

/**
 * Prints active contracts in a stable order.
 *
 * Why:
 * - Active contracts are the bridge between board selection and returns; stable printing helps track lifecycle transitions.
 * - Hero IDs are additionally normalized (sorted) to avoid noise when hero assignment order is non-essential to the UI.
 */
private fun printActive(state: GameState) {
    val xs = state.contracts.active.sortedBy { it.id.value }
    if (xs.isEmpty()) return println("(active empty)")
    for (a in xs) {
        val heroes = a.heroIds.map { it.value }.sorted()
        println("activeId=${a.id.value} boardId=${a.boardContractId.value} status=${a.status} daysRemaining=${a.daysRemaining} heroes=$heroes")
    }
}

/**
 * Prints return packets in a stable order.
 *
 * Why:
 * - Returns are the "player intervention" boundary; sorting by active ID makes it easy to match to lifecycle events.
 * - `requiresClose` is printed explicitly because it controls whether the player must issue `close`.
 */
private fun printReturns(state: GameState) {
    val xs = state.contracts.returns.sortedBy { it.activeContractId.value }
    if (xs.isEmpty()) return println("(returns empty)")
    for (r in xs) {
        println("activeId=${r.activeContractId.value} resolvedDay=${r.resolvedDay} outcome=${r.outcome} trophies=${r.trophiesCount} quality=${r.trophiesQuality} requiresClose=${r.requiresPlayerClose}")
    }
}

/**
 * Formats an [Event] into a single-line, log-friendly representation.
 *
 * Why:
 * - Keeps console output compact while preserving the fields humans use to reason about causality: seq/day/rev/cmdId.
 * - Avoids printing full JSON here to keep the "human view" stable even if serialization evolves.
 * - Each branch pins field ordering and labels to reduce churn in test fixtures and pasted transcripts.
 *
 * Note:
 * - The final `else` branch provides a non-throwing fallback if the event model expands without updating this adapter.
 *   This is intentionally biased toward debuggability over strict exhaustiveness in the console layer.
 */
private fun formatEvent(e: Event): String =
    when (e) {
        is DayStarted -> "E#${e.seq} DayStarted day=${e.day} rev=${e.revision} cmdId=${e.cmdId}"
        is InboxGenerated -> "E#${e.seq} InboxGenerated day=${e.day} rev=${e.revision} cmdId=${e.cmdId} count=${e.count} ids=${e.contractIds.contentToString()}"
        is HeroesArrived -> "E#${e.seq} HeroesArrived day=${e.day} rev=${e.revision} cmdId=${e.cmdId} count=${e.count} ids=${e.heroIds.contentToString()}"
        is ContractPosted -> "E#${e.seq} ContractPosted day=${e.day} rev=${e.revision} cmdId=${e.cmdId} boardId=${e.boardContractId} fromInbox=${e.fromInboxId} rank=${e.rank} fee=${e.fee} salvage=${e.salvage} clientDeposit=${e.clientDeposit}"
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
        is ContractAutoResolved -> "E#${e.seq} ContractAutoResolved day=${e.day} rev=${e.revision} cmdId=${e.cmdId} draftId=${e.draftId} bucket=${e.bucket}"
        is HeroDied -> "E#${e.seq} HeroDied day=${e.day} rev=${e.revision} cmdId=${e.cmdId} heroId=${e.heroId} activeId=${e.activeContractId} boardId=${e.boardContractId}"
    }

/**
 * Prints day analytics derived strictly from the emitted [Event] list and [DaySnapshot] values.
 *
 * NOTE: This block is intentionally unchanged by the Day Report feature.
 */
private fun printDayAnalytics(events: List<Event>, currentSnapshot: DaySnapshot, prevSnapshot: DaySnapshot?) {
    val dayEnded = events.filterIsInstance<DayEnded>().firstOrNull()
    if (dayEnded == null) return

    val curDay = currentSnapshot.day
    val prevDay = prevSnapshot?.day

    val s7Inbox = events.filterIsInstance<InboxGenerated>().sumOf { it.count }
    val s7Arrivals = events.filterIsInstance<HeroesArrived>().sumOf { it.count }

    val resolvedEvents = events.filterIsInstance<ContractResolved>()
    val s8Resolved = resolvedEvents.size
    val outcomeCounts = resolvedEvents.groupingBy { it.outcome }.eachCount()
    val s8Success = outcomeCounts[Outcome.SUCCESS] ?: 0
    val s8Partial = outcomeCounts[Outcome.PARTIAL] ?: 0
    val s8Fail = outcomeCounts[Outcome.FAIL] ?: 0

    val s9Money = currentSnapshot.money
    val s9Trophies = currentSnapshot.trophies

    val moneyDelta: Int? = prevSnapshot?.let { currentSnapshot.money - it.money }
    val trophiesDelta: Int? = prevSnapshot?.let { currentSnapshot.trophies - it.trophies }

    println("─── Day Analytics ───")
    println("S7: day=$curDay prevDay=${prevDay ?: "N/A"} inboxGenerated=+$s7Inbox arrivals=+$s7Arrivals")
    println("S8: resolved=+$s8Resolved success=$s8Success partial=$s8Partial fail=$s8Fail")

    val moneyDeltaStr = moneyDelta?.let { fmtDelta(it) } ?: "(Δ=N/A)"
    val trophiesDeltaStr = trophiesDelta?.let { fmtDelta(it) } ?: "(Δ=N/A)"

    // Keep the S9 line label/order stable; reserved is not present in the DaySnapshot schema in this repo.
    println("S9: money=$s9Money $moneyDeltaStr trophies=$s9Trophies $trophiesDeltaStr")
    println("────────────────────")
}
