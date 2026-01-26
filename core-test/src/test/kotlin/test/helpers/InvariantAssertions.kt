// Split from TestHelpers.kt — invariant helpers and assertions
package test.helpers

import core.invariants.InvariantId
import core.invariants.verifyInvariants
import core.state.initialState
import core.state.GameState
import kotlin.test.*

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
