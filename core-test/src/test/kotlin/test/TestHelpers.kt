// FILE: core-test/src/test/kotlin/test/TestHelper.kt
package test

import core.*
import core.hash.hashEvents
import core.hash.hashState
import core.invariants.InvariantId
import core.invariants.verifyInvariants
import core.primitives.*
import core.rng.Rng
import core.state.GameState
import core.state.initialState
import core.state.*
import kotlin.math.max
import kotlin.reflect.KClass
import kotlin.reflect.full.*
import kotlin.test.*

/**
 * Shared test utilities for PoC scenario replays + invariant/unit helpers + (optional) perf helpers.
 */

// Convenience extension properties to access nested GameState fields
val GameState.inbox get() = contracts.inbox
val GameState.board get() = contracts.board
val GameState.active get() = contracts.active
val GameState.returns get() = contracts.returns
val GameState.nextContractId get() = meta.ids.nextContractId

// -----------------------------------------------------------------------------
// Scenario runner (golden replays)
// -----------------------------------------------------------------------------

data class Scenario(
    val scenarioId: String,
    val stateSeed: UInt,
    val rngSeed: Long,
    val commands: List<Command>,
    val expectedMainEventTypes: List<String>? = null,
    val description: String = ""
)

data class ScenarioResult(
    val finalState: GameState,
    val allEvents: List<Event>,
    val stepResults: List<StepResult>,
    val rngDraws: Long
)

fun runScenario(scenario: Scenario): ScenarioResult {
    var state = initialState(scenario.stateSeed)
    val rng = Rng(scenario.rngSeed)

    val stepResults = mutableListOf<StepResult>()
    val allEvents = mutableListOf<Event>()

    for (cmd in scenario.commands) {
        val result = step(state, cmd, rng)
        stepResults += result
        allEvents += result.events
        state = result.state
    }

    return ScenarioResult(
        finalState = state,
        allEvents = allEvents,
        stepResults = stepResults,
        rngDraws = rng.draws
    )
}

// -----------------------------------------------------------------------------
// Event helpers
// -----------------------------------------------------------------------------

fun mainEvents(events: List<Event>): List<Event> = events.filterNot { it is InvariantViolated }
fun mainEventTypes(events: List<Event>): List<String> = mainEvents(events).map { it::class.simpleName ?: "Unknown" }

fun assertEventTypesMatch(events: List<Event>, expectedTypes: List<String>, message: String = "") {
    val actualTypes = mainEventTypes(events)
    assertEquals(expectedTypes, actualTypes, message.ifBlank { "Event type sequence mismatch" })
}

fun assertEventTypesPresent(events: List<Event>, requiredTypes: Set<String>, message: String = "") {
    val actual = mainEventTypes(events).toSet()
    val missing = requiredTypes - actual
    assertTrue(missing.isEmpty(), message.ifBlank { "Missing event types: $missing; actual=$actual" })
}

fun assertNoRejections(events: List<Event>, message: String = "Expected no command rejections") {
    val rejections = events.filterIsInstance<CommandRejected>()
    assertTrue(
        rejections.isEmpty(),
        message.ifBlank { "Found rejections: ${rejections.map { "${it.cmdType}: ${it.reason} - ${it.detail}" }}" }
    )
}

fun assertNoInvariantViolations(events: List<Event>, message: String = "Expected no invariant violations") {
    val violations = events.filterIsInstance<InvariantViolated>()
    assertTrue(
        violations.isEmpty(),
        message.ifBlank { "Found violations: ${violations.map { "${it.invariantId.code}: ${it.details}" }}" }
    )
}

fun assertSingleRejection(events: List<Event>, expectedReason: RejectReason, message: String = "") {
    val rejections = events.filterIsInstance<CommandRejected>()
    assertEquals(1, rejections.size, message.ifBlank { "Expected exactly 1 rejection, found: ${rejections.size}" })
    val r = rejections.first()
    assertEquals(expectedReason, r.reason, message.ifBlank { "Expected reason=$expectedReason, got=${r.reason} (${r.detail})" })
}

inline fun <reified T : Event> assertEventCount(events: List<Event>, expected: Int, message: String = "") {
    val count = mainEvents(events).filterIsInstance<T>().size
    assertEquals(expected, count, message.ifBlank { "Expected $expected events of ${T::class.simpleName}, actual=$count" })
}

fun assertEventCountByType(events: List<Event>, eventClassName: String, expected: Int, message: String = "") {
    val count = mainEvents(events).count { it::class.simpleName == eventClassName }
    assertEquals(expected, count, message.ifBlank { "Expected $expected events of $eventClassName, actual=$count" })
}

fun assertEventPresentByType(events: List<Event>, eventClassName: String, message: String = "") {
    val ok = mainEvents(events).any { it::class.simpleName == eventClassName }
    assertTrue(ok, message.ifBlank { "Expected event of type $eventClassName to be present" })
}

fun assertEventAbsentByType(events: List<Event>, eventClassName: String, message: String = "") {
    val ok = mainEvents(events).none { it::class.simpleName == eventClassName }
    assertTrue(ok, message.ifBlank { "Expected event of type $eventClassName to be absent" })
}

fun assertSequentialSeq(events: List<Event>, label: String) {
    if (events.isEmpty()) return
    events.forEachIndexed { index, e ->
        assertEquals((index + 1).toLong(), e.seq, "$label: seq must be sequential")
    }
}

fun assertStepOk(events: List<Event>, label: String) {
    assertNoInvariantViolations(events, "$label: must not emit InvariantViolated")
    assertNoRejections(events, "$label: must not reject")
    assertSequentialSeq(events, label)
}

fun printScenarioResult(result: ScenarioResult, includeEvents: Boolean = false) {
    println("=== Scenario Result ===")
    println("Final day: ${result.finalState.meta.dayIndex}")
    println("Final revision: ${result.finalState.meta.revision}")
    println("Final money: ${result.finalState.economy.moneyCopper}")
    println("Final trophies: ${result.finalState.economy.trophiesStock}")
    println("Total RNG draws: ${result.rngDraws}")
    println("Total events: ${result.allEvents.size}")

    if (includeEvents) {
        println("\n--- Events ---")
        result.allEvents.forEachIndexed { index, event ->
            println("[$index] ${event::class.simpleName} (day=${event.day}, rev=${event.revision}, seq=${event.seq})")
        }
    }

    println("\n--- Event Types (main only) ---")
    println(mainEventTypes(result.allEvents).joinToString(", "))

    val rejections = result.allEvents.filterIsInstance<CommandRejected>()
    if (rejections.isNotEmpty()) {
        println("\n⚠ Rejections: ${rejections.size}")
        rejections.forEach { println("  - ${it.cmdType}: ${it.reason} (${it.detail})") }
    }

    val violations = result.allEvents.filterIsInstance<InvariantViolated>()
    if (violations.isNotEmpty()) {
        println("\n⚠ Invariant Violations: ${violations.size}")
        violations.forEach { println("  - ${it.invariantId.code}: ${it.details}") }
    }

    println("=======================\n")
}

// -----------------------------------------------------------------------------
// Replay determinism
// -----------------------------------------------------------------------------

fun assertReplayDeterminism(scenario: Scenario, message: String = "") {
    val r1 = runScenario(scenario)
    val r2 = runScenario(scenario)

    val stateHash1 = hashState(r1.finalState)
    val stateHash2 = hashState(r2.finalState)
    assertEquals(stateHash1, stateHash2, message.ifBlank { "State hashes must match: $stateHash1 vs $stateHash2" })

    val eventsHash1 = hashEvents(r1.allEvents)
    val eventsHash2 = hashEvents(r2.allEvents)
    assertEquals(eventsHash1, eventsHash2, message.ifBlank { "Event hashes must match: $eventsHash1 vs $eventsHash2" })

    assertEquals(r1.rngDraws, r2.rngDraws, message.ifBlank { "RNG draws must match: ${r1.rngDraws} vs ${r2.rngDraws}" })
}

// -----------------------------------------------------------------------------
// Invariant helpers (KISS DSL for tiny unit tests)
// -----------------------------------------------------------------------------

fun baseState(seed: UInt = 42u): GameState = initialState(seed)

inline fun state(seed: UInt = 42u, block: GameState.() -> GameState): GameState = baseState(seed).block()

fun allViolations(state: GameState) = verifyInvariants(state)
fun hasViolation(state: GameState, invariantId: InvariantId): Boolean =
    verifyInvariants(state).any { it.invariantId == invariantId }

fun violationsOf(state: GameState, invariantId: InvariantId) =
    verifyInvariants(state).filter { it.invariantId == invariantId }

fun assertNoViolations(state: GameState, invariantId: InvariantId, message: String = "") {
    val v = violationsOf(state, invariantId)
    assertTrue(v.isEmpty(), buildString {
        if (message.isNotBlank()) append(message).append("\n")
        append("Expected no violations of ").append(invariantId.code).append(", got=").append(v)
    })
}

fun assertHasViolation(state: GameState, invariantId: InvariantId, message: String = "") {
    val v = violationsOf(state, invariantId)
    assertTrue(v.isNotEmpty(), buildString {
        if (message.isNotBlank()) append(message).append("\n")
        append("Expected at least one violation of ").append(invariantId.code).append(", got=").append(v)
    })
}

fun assertStateValid(state: GameState, message: String = "") {
    val v = allViolations(state)
    assertTrue(v.isEmpty(), buildString {
        if (message.isNotBlank()) append(message).append("\n")
        append("Expected no invariant violations, got=").append(v)
    })
}

fun assertViolationDetailsDeterministic(state: GameState, message: String = "") {
    val v1 = allViolations(state)
    val v2 = allViolations(state)
    assertEquals(v1.size, v2.size, message.ifBlank { "Violation count must be deterministic" })
    v1.zip(v2).forEach { (a, b) ->
        assertEquals(a.invariantId, b.invariantId, message.ifBlank { "InvariantId must be deterministic" })
        assertEquals(a.details, b.details, message.ifBlank { "Violation details must be deterministic" })
    }
}

// Overloads that build a derived state inline (keeps tests short)
inline fun expectViolation(
    invariantId: InvariantId,
    seed: UInt = 42u,
    message: String = "",
    crossinline mutate: GameState.() -> GameState
) = assertHasViolation(state(seed) { mutate() }, invariantId, message)

inline fun expectNoViolation(
    invariantId: InvariantId,
    seed: UInt = 42u,
    message: String = "",
    crossinline mutate: GameState.() -> GameState
) = assertNoViolations(state(seed) { mutate() }, invariantId, message)

// Backwards-compatible overloads (seed first) matching existing tests that call expectViolation(seed, InvariantId)
inline fun expectViolation(
    seed: UInt = 42u,
    invariantId: InvariantId,
    message: String = "",
    crossinline mutate: GameState.() -> GameState
) = assertHasViolation(state(seed) { mutate() }, invariantId, message)

inline fun expectNoViolation(
    seed: UInt = 42u,
    invariantId: InvariantId,
    message: String = "",
    crossinline mutate: GameState.() -> GameState
) = assertNoViolations(state(seed) { mutate() }, invariantId, message)

// Handy shortcuts used in multiple tests
fun assertNoLockedBoardViolations(state: GameState, message: String = "") =
    assertNoViolations(state, InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE, message)

fun assertHasLockedBoardViolation(state: GameState, message: String = "") =
    assertHasViolation(state, InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE, message)

// -----------------------------------------------------------------------------
// Contract inbox helpers (tiny, 20/80)
// -----------------------------------------------------------------------------

fun assertContractPresentInInbox(state: GameState, contractId: Int, message: String = "") {
    val exists = state.inbox.any { it.id.value == contractId }
    assertTrue(exists, message.ifBlank { "Expected contract $contractId to be present in inbox" })
}

fun assertContractAbsentInInbox(state: GameState, contractId: Int, message: String = "") {
    val exists = state.inbox.any { it.id.value == contractId }
    assertTrue(!exists, message.ifBlank { "Expected contract $contractId to be absent from inbox" })
}

// -----------------------------------------------------------------------------
// Economy helpers
// -----------------------------------------------------------------------------

fun assertReservedCopper(state: GameState, expected: Int, message: String = "") {
    val actual = state.economy.reservedCopper
    assertEquals(expected, actual, message.ifBlank { "Expected reservedCopper=$expected, actual=$actual" })
}

fun assertAvailableCopper(state: GameState, expected: Int, message: String = "") {
    val actual = state.economy.moneyCopper - state.economy.reservedCopper
    assertEquals(expected, actual, message.ifBlank { "Expected available money=$expected, actual=$actual" })
}

// -----------------------------------------------------------------------------
// Perf helpers (P3 / manual runs)
// NOTE: stores per-step times in memory; 1_000_000 steps ~= 8MB (ok). Much larger -> consider sampling.
// -----------------------------------------------------------------------------

data class PerfStats(
    val label: String,
    val steps: Int,
    val nanosTotal: Long,
    val nanosAvg: Long,
    val nanosP50: Long,
    val nanosP95: Long,
    val nanosMax: Long,
    val eventsTotal: Long,
    val invariantViolations: Long,
    val rejections: Long,
    val rngDraws: Long
)

private fun percentile(sorted: List<Long>, p: Double): Long {
    if (sorted.isEmpty()) return 0L
    val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
    return sorted[idx]
}

fun runPerfLoop(
    label: String,
    stateSeed: UInt,
    rngSeed: Long,
    steps: Int,
    warmup: Int = minOf(200, max(0, steps / 10)),
    cmdFactory: (state: GameState, cmdId: Long, i: Int) -> Command
): PerfStats {
    var state = initialState(stateSeed)
    val rng = Rng(rngSeed)
    var cmdId = 1L

    repeat(warmup) { i ->
        val cmd = cmdFactory(state, cmdId++, i)
        state = step(state, cmd, rng).state
    }

    val times = LongArray(steps)
    var eventsTotal = 0L
    var invTotal = 0L
    var rejTotal = 0L

    repeat(steps) { i ->
        val cmd = cmdFactory(state, cmdId++, i)

        val t0 = System.nanoTime()
        val res = step(state, cmd, rng)
        val t1 = System.nanoTime()

        times[i] = (t1 - t0)
        state = res.state

        eventsTotal += res.events.size.toLong()
        invTotal += res.events.count { it is InvariantViolated }.toLong()
        rejTotal += res.events.count { it is CommandRejected }.toLong()
    }

    val total = times.sum()
    val avg = if (steps == 0) 0L else total / steps
    val sorted = times.asList().sorted()

    return PerfStats(
        label = label,
        steps = steps,
        nanosTotal = total,
        nanosAvg = avg,
        nanosP50 = percentile(sorted, 0.50),
        nanosP95 = percentile(sorted, 0.95),
        nanosMax = sorted.lastOrNull() ?: 0L,
        eventsTotal = eventsTotal,
        invariantViolations = invTotal,
        rejections = rejTotal,
        rngDraws = rng.draws
    )
}

fun printPerf(stats: PerfStats) {
    fun nsToMs(ns: Long) = ns / 1_000_000.0
    val avgUs = stats.nanosAvg / 1_000.0
    println(
        """
        ── PERF: ${stats.label} ──
        steps=${stats.steps}
        total=${nsToMs(stats.nanosTotal)} ms
        avg=${avgUs} µs  p50=${stats.nanosP50 / 1_000.0} µs  p95=${stats.nanosP95 / 1_000.0} µs  max=${stats.nanosMax / 1_000.0} µs
        eventsTotal=${stats.eventsTotal}  inv=${stats.invariantViolations}  rej=${stats.rejections}
        rngDraws=${stats.rngDraws}
        """.trimIndent()
    )
}

// -----------------------------------------------------------------------------
// Reflection helpers for sealed-class inspection (used by manifest/coverage tests)
// -----------------------------------------------------------------------------

/** Return simple names of all sealed subclasses for the given KClass (filters nulls). */
fun sealedSubclassNamesOf(kclass: KClass<*>): Set<String> =
    kclass.sealedSubclasses.mapNotNull { it.simpleName }.toSet()

/** Assert that the given expected names are present among the sealed subclasses. */
fun assertSealedSubclassNamesContain(kclass: KClass<*>, expected: Set<String>, message: String = "") {
    val actual = sealedSubclassNamesOf(kclass)
    val missing = expected - actual
    assertTrue(missing.isEmpty(), message.ifBlank { "Missing ${kclass.simpleName} subclasses: $missing; actual=$actual" })
}

/** Return sealed-subclass names present that are not in the allowed set (extra items). */
fun sealedSubclassesExtra(kclass: KClass<*>, allowed: Set<String>): Set<String> =
    sealedSubclassNamesOf(kclass) - allowed

/** Assert that a specific sealed-subclass by simple name exists. */
fun assertSealedSubclassExists(kclass: KClass<*>, name: String, message: String = "") {
    val ok = sealedSubclassNamesOf(kclass).contains(name)
    assertTrue(ok, message.ifBlank { "Expected ${kclass.simpleName} to contain subclass $name" })
}

/** Return names of sealed subclasses that do NOT declare the given Java field name. */
fun sealedSubclassesMissingField(kclass: KClass<*>, fieldName: String): Set<String> =
    kclass.sealedSubclasses.filter { sub -> sub.java.declaredFields.none { it.name == fieldName } }
        .mapNotNull { it.simpleName }.toSet()

/** Assert that all sealed subclasses declare the given Java field name. */
fun assertAllSealedSubclassesHaveField(kclass: KClass<*>, fieldName: String, message: String = "") {
    val missing = sealedSubclassesMissingField(kclass, fieldName)
    assertTrue(missing.isEmpty(), message.ifBlank { "Sealed subclasses of ${kclass.simpleName} missing field '$fieldName': $missing" })
}

/** Assert that all sealed subclasses declare the required Java field names. */
fun assertSealedSubclassesHaveFields(kclass: KClass<*>, requiredFields: Set<String>, message: String = "") {
    val bad = kclass.sealedSubclasses.mapNotNull { sub ->
        val fieldNames = sub.java.declaredFields.map { it.name }.toSet()
        val missing = requiredFields - fieldNames
        if (missing.isNotEmpty()) "${sub.simpleName}: missing=$missing" else null
    }
    assertTrue(bad.isEmpty(), message.ifBlank { "Sealed subclasses of ${kclass.simpleName} missing required fields: $bad" })
}

// -----------------------------------------------------------------------------
// Locked-board invariant fixtures + assertions (keeps tests minimal)
// -----------------------------------------------------------------------------

data class LockedBoardFixture(
    val initial: GameState,
    val rng: Rng
)

fun boardContract(
    id: Long = 1L,
    postedDay: Int = 0,
    title: String = "Test",
    rank: Rank = Rank.F,
    fee: Int = 0,
    salvage: SalvagePolicy = SalvagePolicy.GUILD,
    baseDifficulty: Int = 1,
    status: BoardStatus = BoardStatus.LOCKED
): BoardContract = BoardContract(
    id = ContractId(id.toInt()),
    postedDay = postedDay,
    title = title,
    rank = rank,
    fee = fee,
    salvage = salvage,
    baseDifficulty = baseDifficulty,
    status = status
)

fun hero(
    id: Long,
    name: String = "Hero #$id",
    rank: Rank = Rank.F,
    klass: HeroClass = HeroClass.WARRIOR,
    traits: Traits = Traits(greed = 50, honesty = 50, courage = 50),
    status: HeroStatus = HeroStatus.ON_MISSION,
    historyCompleted: Int = 0
): Hero = Hero(
    id = HeroId(id.toInt()),
    name = name,
    rank = rank,
    klass = klass,
    traits = traits,
    status = status,
    historyCompleted = historyCompleted
)

fun active(
    id: Long,
    heroIds: List<Long>,
    status: ActiveStatus,
    boardContractId: Long = 1L,
    takenDay: Int = 1,
    daysRemaining: Int = 0
): ActiveContract = ActiveContract(
    id = ActiveContractId(id.toInt()),
    boardContractId = ContractId(boardContractId.toInt()),
    takenDay = takenDay,
    daysRemaining = daysRemaining,
    heroIds = heroIds.map { HeroId(it.toInt()) },
    status = status
)

fun returnPacket(
    activeId: Long,
    heroIds: List<Long>,
    boardContractId: Long = 1L,
    resolvedDay: Int = 1,
    outcome: Outcome = Outcome.SUCCESS,
    trophiesCount: Int = 1,
    trophiesQuality: Quality = Quality.OK,
    reasonTags: List<String> = emptyList(),
    requiresPlayerClose: Boolean = true,
    suspectedTheft: Boolean = false
): ReturnPacket = ReturnPacket(
    boardContractId = ContractId(boardContractId.toInt()),
    heroIds = heroIds.map { HeroId(it.toInt()) },
    activeContractId = ActiveContractId(activeId.toInt()),
    resolvedDay = resolvedDay,
    outcome = outcome,
    trophiesCount = trophiesCount,
    trophiesQuality = trophiesQuality,
    reasonTags = reasonTags,
    requiresPlayerClose = requiresPlayerClose,
    suspectedTheft = suspectedTheft
)

fun lockedBoardState(
    seed: UInt = 42u,
    boardStatus: BoardStatus = BoardStatus.LOCKED,
    actives: List<ActiveContract>,
    returns: List<ReturnPacket>,
    heroes: List<Hero>,
    economy: EconomyState = EconomyState(
        moneyCopper = 100,
        reservedCopper = 0,
        trophiesStock = 0
    )
): GameState = initialState(seed).copy(
    economy = economy,
    contracts = ContractState(
        inbox = emptyList(),
        board = listOf(boardContract(status = boardStatus)),
        active = actives,
        returns = returns
    ),
    heroes = HeroState(
        roster = heroes,
        arrivalsToday = emptyList()
    )
)

fun lockedBoardFixture(
    seed: UInt = 42u,
    rngSeed: Long = 100L,
    boardStatus: BoardStatus = BoardStatus.LOCKED,
    actives: List<ActiveContract>,
    returns: List<ReturnPacket>,
    heroes: List<Hero>,
    economy: EconomyState = EconomyState(
        moneyCopper = 100,
        reservedCopper = 0,
        trophiesStock = 0
    )
): LockedBoardFixture = LockedBoardFixture(
    initial = lockedBoardState(
        seed = seed,
        boardStatus = boardStatus,
        actives = actives,
        returns = returns,
        heroes = heroes,
        economy = economy
    ),
    rng = Rng(rngSeed)
)

fun closeReturn(state: GameState, activeContractId: Long, cmdId: Long, rng: Rng): StepResult =
    step(state, CloseReturn(activeContractId = activeContractId, cmdId = cmdId), rng)

fun boardStatusOf(state: GameState, boardContractId: Long = 1L): BoardStatus =
    state.contracts.board.first { it.id.value.toLong() == boardContractId }.status

fun assertBoardStatus(state: GameState, expected: BoardStatus, boardContractId: Long = 1L, message: String = "") {
    val actual = boardStatusOf(state, boardContractId)
    assertEquals(expected, actual, message.ifBlank { "Expected board[$boardContractId]=$expected, actual=$actual" })
}

fun activeStatusOf(state: GameState, activeContractId: Long): ActiveStatus =
    state.contracts.active.first { it.id.value.toLong() == activeContractId }.status

fun assertActiveStatusIn(state: GameState, activeContractId: Long, allowed: Set<ActiveStatus>, message: String = "") {
    val actual = activeStatusOf(state, activeContractId)
    assertTrue(actual in allowed, message.ifBlank { "Expected active[$activeContractId] in $allowed, actual=$actual" })
}

fun assertNoInvariantViolation(events: List<Event>, invariantId: InvariantId, message: String = "") {
    val v = events.filterIsInstance<InvariantViolated>().filter { it.invariantId == invariantId }
    assertTrue(v.isEmpty(), message.ifBlank { "Expected no InvariantViolated(${invariantId.code}) in events, got=$v" })
}

// -----------------------------------------------------------------------------
// Trophy pipeline fixtures + assertions (keeps tests minimal)
// -----------------------------------------------------------------------------

data class TrophyPipelineFixture(
    val initial: GameState,
    val rng: Rng
)

data class TrophySpec(
    val contractId: Long,
    val activeId: Long = contractId,
    val heroId: Long = contractId,
    val title: String = "Test",
    val boardRank: Rank = Rank.F,
    val fee: Int = 0,
    val baseDifficulty: Int = 1,
    val boardStatus: BoardStatus = BoardStatus.LOCKED,
    val daysRemaining: Int = 1,
    val heroRank: Rank = Rank.F,
    val heroClass: HeroClass = HeroClass.WARRIOR,
    val heroStatus: HeroStatus = HeroStatus.ON_MISSION,
    val greed: Int = 0,
    val honesty: Int = 50,
    val courage: Int = 50,
    val heroHistoryCompleted: Int = 0
)

fun trophySpec(
    contractId: Long,
    activeId: Long = contractId,
    heroId: Long = contractId,
    title: String = "Test",
    boardRank: Rank = Rank.F,
    fee: Int = 0,
    baseDifficulty: Int = 1,
    boardStatus: BoardStatus = BoardStatus.LOCKED,
    daysRemaining: Int = 1,
    heroRank: Rank = Rank.F,
    heroClass: HeroClass = HeroClass.WARRIOR,
    heroStatus: HeroStatus = HeroStatus.ON_MISSION,
    greed: Int = 0,
    honesty: Int = 50,
    courage: Int = 50,
    heroHistoryCompleted: Int = 0
): TrophySpec = TrophySpec(
    contractId = contractId,
    activeId = activeId,
    heroId = heroId,
    title = title,
    boardRank = boardRank,
    fee = fee,
    baseDifficulty = baseDifficulty,
    boardStatus = boardStatus,
    daysRemaining = daysRemaining,
    heroRank = heroRank,
    heroClass = heroClass,
    heroStatus = heroStatus,
    greed = greed,
    honesty = honesty,
    courage = courage,
    heroHistoryCompleted = heroHistoryCompleted
)

// Minimal fixture builders and helpers used by trophy tests

fun trophyResolveFixture(
    seed: UInt = 42u,
    rngSeed: Long = 100L,
    specs: List<TrophySpec>
): TrophyPipelineFixture {
    // Build a simple state with provided specs turned into board/active/hero/returns
    var state = initialState(seed)

    val board = specs.map { s ->
        BoardContract(
            id = ContractId(s.contractId.toInt()),
            postedDay = 0,
            title = s.title,
            rank = s.boardRank,
            fee = s.fee,
            salvage = SalvagePolicy.GUILD,
            baseDifficulty = s.baseDifficulty,
            status = s.boardStatus
        )
    }

    val actives = specs.map { s ->
        ActiveContract(
            id = ActiveContractId(s.activeId.toInt()),
            boardContractId = ContractId(s.contractId.toInt()),
            takenDay = 0,
            daysRemaining = s.daysRemaining,
            heroIds = listOf(HeroId(s.heroId.toInt())),
            status = ActiveStatus.WIP
        )
    }

    val heroes = specs.map { s ->
        Hero(
            id = HeroId(s.heroId.toInt()),
            name = "Hero #${s.heroId}",
            rank = s.heroRank,
            klass = s.heroClass,
            traits = Traits(greed = s.greed, honesty = s.honesty, courage = s.courage),
            status = s.heroStatus,
            historyCompleted = s.heroHistoryCompleted
        )
    }

    state = state.copy(
        contracts = ContractState(
            inbox = emptyList(),
            board = board,
            active = actives,
            returns = emptyList()
        ),
        heroes = HeroState(roster = heroes, arrivalsToday = emptyList())
    )

    return TrophyPipelineFixture(initial = state, rng = Rng(rngSeed))
}

fun trophyCloseFixture(
    rngSeed: Long = 100L,
    trophiesCount: Int,
    trophiesQuality: Quality,
    initialMoney: Int,
    initialReserved: Int,
    initialStock: Int
): TrophyPipelineFixture {
    val state = initialState(42u).copy(
        economy = EconomyState(moneyCopper = initialMoney, reservedCopper = initialReserved, trophiesStock = initialStock),
        contracts = ContractState(
            inbox = emptyList(),
            board = listOf(boardContract()),
            active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
            returns = listOf(returnPacket(activeId = 1L, heroIds = listOf(1L), trophiesCount = trophiesCount, trophiesQuality = trophiesQuality))
        ),
        heroes = HeroState(roster = listOf(hero(1L)), arrivalsToday = emptyList())
    )
    return TrophyPipelineFixture(initial = state, rng = Rng(rngSeed))
}

fun trophyStockFixture(
    rngSeed: Long = 100L,
    moneyCopper: Int,
    reservedCopper: Int,
    trophiesStock: Int
): TrophyPipelineFixture {
    val state = initialState(42u).copy(
        economy = EconomyState(moneyCopper = moneyCopper, reservedCopper = reservedCopper, trophiesStock = trophiesStock),
        contracts = ContractState(inbox = emptyList(), board = listOf(boardContract()), active = emptyList(), returns = emptyList()),
        heroes = HeroState(roster = emptyList(), arrivalsToday = emptyList())
    )
    return TrophyPipelineFixture(initial = state, rng = Rng(rngSeed))
}

// Wrapper helpers that call the reducer step to perform actions used in tests
fun advanceDay(state: GameState, cmdId: Long, rng: Rng): StepResult = step(state, AdvanceDay(cmdId = cmdId), rng)

fun sellAllTrophies(state: GameState, cmdId: Long, rng: Rng): StepResult = step(state, SellTrophies(amount = 0, cmdId = cmdId), rng)

// Assertions used by trophy tests

fun assertSingleResolvedCreatesSingleReturn(state: GameState, events: List<Event>) {
    val resolved = events.filter { it::class.simpleName == "ContractResolved" }
    if (resolved.size != 1) {
        println("DEBUG: Events emitted: ${events.map { it::class.simpleName }}")
        println("DEBUG: State returns size=${state.contracts.returns.size}")
    }
    assertEquals(1, resolved.size, "Expected exactly one ContractResolved event")
    assertEquals(1, state.contracts.returns.size, "Expected one return present in state")
}

fun assertTrophiesDepositedAndReturnRemoved(before: GameState, after: GameState, activeContractId: Long, expectedDeposited: Int) {
    val beforeStock = before.economy.trophiesStock
    val afterStock = after.economy.trophiesStock
    assertEquals(beforeStock + expectedDeposited, afterStock, "Trophies should be deposited to stock")
    val stillHasReturn = after.contracts.returns.any { it.activeContractId.value.toLong() == activeContractId }
    assertTrue(!stillHasReturn, "Return should be removed after close")
}

fun assertSellAllApplied(before: GameState, after: GameState, events: List<Event>) {
    // If seller sold all trophies, stock should be zero and money increased accordingly
    val moneyDelta = after.economy.moneyCopper - before.economy.moneyCopper
    val stockDelta = before.economy.trophiesStock - after.economy.trophiesStock
    assertTrue(stockDelta >= 0, "Stock must not increase on sell")
    assertEquals(stockDelta, moneyDelta, "Money gained should equal trophies sold (1 copper per trophy in reducer)")
}

fun assertResolvedCount(events: List<Event>, expected: Int): List<ReturnPacket> {
    val resolvedEvents = events.filter { it::class.simpleName == "ContractResolved" }.map { e -> e as core.ContractResolved }
    assertEquals(expected, resolvedEvents.size, "Expected $expected resolved events")
    // Synthesize ReturnPacket objects with minimal fields for downstream checks
    return resolvedEvents.map { ev ->
        ReturnPacket(
            activeContractId = ActiveContractId(ev.activeContractId),
            boardContractId = ContractId(1),
            heroIds = emptyList(),
            resolvedDay = ev.day,
            outcome = ev.outcome,
            trophiesCount = ev.trophiesCount,
            trophiesQuality = ev.quality,
            reasonTags = emptyList(),
            requiresPlayerClose = false,
            suspectedTheft = false
        )
    }
}

fun assertResolvedTrophiesValid(packet: ReturnPacket) {
    // basic sanity: trophiesCount non-negative
    assertTrue(packet.trophiesCount >= 0, "Resolved trophies must be non-negative")
}

fun trophyE2EFixture(
    seed: UInt = 42u,
    rngSeed: Long = 200L,
    initialMoney: Int,
    initialReserved: Int,
    initialStock: Int,
    contractFee: Int,
    baseDifficulty: Int,
    heroRank: Rank,
    heroHistoryCompleted: Int
): TrophyPipelineFixture {
    // Create a state that can resolve a return and allow selling
    val s = initialState(seed).copy(
        economy = EconomyState(moneyCopper = initialMoney, reservedCopper = initialReserved, trophiesStock = initialStock),
        contracts = ContractState(
            inbox = emptyList(),
            board = listOf(boardContract(fee = contractFee, baseDifficulty = baseDifficulty)),
            active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY, daysRemaining = 0)),
            returns = listOf(returnPacket(activeId = 1L, heroIds = listOf(1L), trophiesCount = 1))
        ),
        heroes = HeroState(roster = listOf(hero(id = 1L, status = HeroStatus.AVAILABLE, historyCompleted = heroHistoryCompleted)), arrivalsToday = emptyList())
    )
    return TrophyPipelineFixture(initial = s, rng = Rng(rngSeed))
}

fun assertEndToEndTrophyFlow(fx: TrophyPipelineFixture) {
    // 1) close return -> deposit trophies
    val r1 = closeReturn(fx.initial, activeContractId = 1L, cmdId = 1L, rng = fx.rng)
    assertTrophiesDepositedAndReturnRemoved(before = fx.initial, after = r1.state, activeContractId = 1L, expectedDeposited = 1)

    // 2) sell all -> money increases
    val r2 = sellAllTrophies(r1.state, cmdId = 2L, rng = fx.rng)
    assertSellAllApplied(before = r1.state, after = r2.state, events = r2.events)
}
