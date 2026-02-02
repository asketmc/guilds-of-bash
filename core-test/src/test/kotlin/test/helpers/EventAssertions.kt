// Split from TestHelpers.kt — event-focused assertions
package test.helpers

import core.*
import kotlin.test.*

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

/** Require exactly one main event (excluding InvariantViolated) of type T and return it. */
inline fun <reified T : Event> requireSingleMainEvent(events: List<Event>, message: String = ""): T {
    val mains = mainEvents(events)
    assertEquals(1, mains.size, message.ifBlank { "Expected exactly one main event, found: ${mains.size}" })
    val e = mains[0]
    assertTrue(e is T, message.ifBlank { "Expected event of type ${T::class.simpleName}, actual=${e::class.simpleName}" })
    return e as T
}

fun printScenarioResult(result: ScenarioResult, includeEvents: Boolean = false) {
    // Use TestLog for deterministic capture in test runs
    TestLog.log("=== Scenario Result ===")
    TestLog.log("Final day: ${result.finalState.meta.dayIndex}")
    TestLog.log("Final revision: ${result.finalState.meta.revision}")
    TestLog.log("Final money: ${result.finalState.economy.moneyCopper}")
    TestLog.log("Final trophies: ${result.finalState.economy.trophiesStock}")
    TestLog.log("Total RNG draws: ${result.rngDraws}")
    TestLog.log("Total events: ${result.allEvents.size}")

    if (includeEvents) {
        TestLog.log("\n--- Events ---")
        result.allEvents.forEachIndexed { index, event ->
            TestLog.log("[$index] ${event::class.simpleName} (day=${event.day}, rev=${event.revision}, seq=${event.seq})")
        }
    }

    TestLog.log("\n--- Event Types (main only) ---")
    TestLog.log(mainEventTypes(result.allEvents).joinToString(", "))

    val rejections = result.allEvents.filterIsInstance<CommandRejected>()
    if (rejections.isNotEmpty()) {
        TestLog.log("\n⚠ Rejections: ${rejections.size}")
        rejections.forEach { TestLog.log("  - ${it.cmdType}: ${it.reason} (${it.detail})") }
    }

    val violations = result.allEvents.filterIsInstance<InvariantViolated>()
    if (violations.isNotEmpty()) {
        TestLog.log("\n⚠ Invariant Violations: ${violations.size}")
        violations.forEach { TestLog.log("  - ${it.invariantId.code}: ${it.details}") }
    }

    TestLog.log("=======================\n")
}
