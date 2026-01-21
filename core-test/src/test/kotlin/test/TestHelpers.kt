package test

import core.*
import core.rng.Rng
import core.state.GameState
import core.state.initialState
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Shared test utilities for PoC scenario replays.
 */

// Convenience extension properties for GameState to access nested properties
val GameState.inbox get() = contracts.inbox
val GameState.board get() = contracts.board
val GameState.active get() = contracts.active
val GameState.returns get() = contracts.returns
val GameState.nextContractId get() = meta.ids.nextContractId

/**
 * Scenario definition for golden replay tests.
 *
 * @property scenarioId Unique identifier for the scenario (e.g., "GR1_happy_path")
 * @property stateSeed Seed for initial state generation
 * @property rngSeed Seed for RNG (deterministic outcome generation)
 * @property commands List of commands to execute in sequence
 * @property expectedMainEventTypes Expected event type sequence (ignoring InvariantViolated)
 */
data class Scenario(
    val scenarioId: String,
    val stateSeed: UInt,
    val rngSeed: Long,
    val commands: List<Command>,
    val expectedMainEventTypes: List<String>? = null,
    val description: String = ""
)

/**
 * Result of running a scenario.
 *
 * @property finalState Final game state after all commands
 * @property allEvents All events emitted across all steps (flattened)
 * @property stepResults Individual step results for analysis
 * @property rngDraws Total RNG draws consumed
 */
data class ScenarioResult(
    val finalState: GameState,
    val allEvents: List<Event>,
    val stepResults: List<StepResult>,
    val rngDraws: Long
)

/**
 * Run a scenario and return the result.
 *
 * This function:
 * - Initializes state with the given seed
 * - Executes commands sequentially
 * - Collects all events across steps
 * - Returns final state and full event history
 *
 * @param scenario The scenario to run
 * @return ScenarioResult with final state, events, and metadata
 */
fun runScenario(scenario: Scenario): ScenarioResult {
    var state = initialState(scenario.stateSeed)
    val rng = Rng(scenario.rngSeed)
    val stepResults = mutableListOf<StepResult>()
    val allEvents = mutableListOf<Event>()

    for (cmd in scenario.commands) {
        val result = step(state, cmd, rng)
        stepResults.add(result)
        allEvents.addAll(result.events)
        state = result.state
    }

    return ScenarioResult(
        finalState = state,
        allEvents = allEvents,
        stepResults = stepResults,
        rngDraws = rng.draws
    )
}

/**
 * Assert that event types match expected sequence (main events only, excluding InvariantViolated).
 *
 * This helper:
 * - Filters out InvariantViolated events
 * - Extracts event type names (simple class names)
 * - Asserts exact sequence match
 *
 * Useful for regression testing: if event sequence changes, test breaks.
 *
 * @param events List of events to check
 * @param expectedTypes List of expected event type names (simple class names)
 * @param message Custom assertion message
 */
fun assertEventTypesMatch(events: List<Event>, expectedTypes: List<String>, message: String = "") {
    val mainEvents = events.filterNot { it is InvariantViolated }
    val actualTypes = mainEvents.map { it::class.simpleName ?: "Unknown" }

    assertEquals(
        expectedTypes,
        actualTypes,
        "$message\nExpected: $expectedTypes\nActual: $actualTypes"
    )
}

/**
 * Assert that specific event types are present (order-agnostic).
 *
 * @param events List of events to check
 * @param requiredTypes Set of event type names that must be present
 * @param message Custom assertion message
 */
fun assertEventTypesPresent(events: List<Event>, requiredTypes: Set<String>, message: String = "") {
    val mainEvents = events.filterNot { it is InvariantViolated }
    val actualTypes = mainEvents.map { it::class.simpleName ?: "Unknown" }.toSet()

    val missing = requiredTypes - actualTypes
    assertTrue(
        missing.isEmpty(),
        "$message\nMissing event types: $missing\nActual events: $actualTypes"
    )
}

/**
 * Assert that no CommandRejected events are present (happy path validation).
 *
 * @param events List of events to check
 * @param message Custom assertion message
 */
fun assertNoRejections(events: List<Event>, message: String = "Expected no command rejections") {
    val rejections = events.filterIsInstance<CommandRejected>()
    assertTrue(
        rejections.isEmpty(),
        "$message\nFound rejections: ${rejections.map { "${it.cmdType}: ${it.reason} - ${it.detail}" }}"
    )
}

/**
 * Assert that no InvariantViolated events are present (structural validity check).
 *
 * @param events List of events to check
 * @param message Custom assertion message
 */
fun assertNoInvariantViolations(events: List<Event>, message: String = "Expected no invariant violations") {
    val violations = events.filterIsInstance<InvariantViolated>()
    assertTrue(
        violations.isEmpty(),
        "$message\nFound violations: ${violations.map { "${it.invariantId.code}: ${it.details}" }}"
    )
}

/**
 * Assert that exactly one CommandRejected event is present with expected reason.
 *
 * @param events List of events to check
 * @param expectedReason Expected RejectReason
 * @param message Custom assertion message
 */
fun assertSingleRejection(events: List<Event>, expectedReason: RejectReason, message: String = "") {
    val rejections = events.filterIsInstance<CommandRejected>()
    assertEquals(1, rejections.size, "$message\nExpected exactly 1 rejection, found: ${rejections.size}")

    val rejection = rejections.first()
    assertEquals(
        expectedReason,
        rejection.reason,
        "$message\nExpected reason: $expectedReason, actual: ${rejection.reason} (${rejection.detail})"
    )
}

/**
 * Pretty-print scenario result for debugging.
 *
 * @param result The scenario result to print
 * @param includeEvents Whether to print all events (can be verbose)
 */
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

    val mainEvents = result.allEvents.filterNot { it is InvariantViolated }
    println("\n--- Event Types (main only) ---")
    println(mainEvents.map { it::class.simpleName }.joinToString(", "))

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
