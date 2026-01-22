package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.*
import core.rng.Rng
import core.state.*
import core.invariants.verifyInvariants
import kotlin.test.*

/**
 * P0 CRITICAL: Reducer critical behavior tests.
 * Core loop functionality - must pass for app to be functional.
 */
@P0
@Smoke
class P1_009_ReducerCriticalTest {

    @Test
    fun `state transitions preserve invariants on simple steps`() {
        val rng = Rng(100L)
        var state = initialState(42u)

        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        val newState = result.state

        val violations = verifyInvariants(newState)
        assertTrue(violations.isEmpty(), "No invariants should be violated after a typical day advance")
    }

    @Test
    fun `applying command with invalid cmdId is rejected`() {
        val state = initialState(42u)
        val cmd = AdvanceDay(cmdId = -1L)

        val result = canApply(state, cmd)

        assertTrue(result is ValidationResult.Rejected)
    }
}
