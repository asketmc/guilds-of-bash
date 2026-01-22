// FILE: core-test/src/test/kotlin/test/TestHelper.kt
package test

import core.*
import core.hash.hashEvents
import core.hash.hashState
import core.invariants.InvariantId
import core.invariants.verifyInvariants
import core.rng.Rng
import core.state.GameState
import core.state.initialState
import kotlin.math.max
import kotlin.test.*
import kotlin.reflect.KClass
import kotlin.reflect.full.*

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
