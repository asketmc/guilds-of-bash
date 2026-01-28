package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.rng.Rng
import core.state.*
import test.helpers.*
import kotlin.test.*

/**
 * P0 CRITICAL: Reducer critical behavior tests.
 * Core loop functionality - must pass for app to be functional.
 */
@P0
@Smoke
class ReducerCriticalTest {

    @Test
    fun `state transitions preserve invariants on simple steps`() {
        val rng = Rng(100L)
        val state = baseState()

        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        val newState = result.state

        assertStateValid(newState, "No invariants should be violated after a typical day advance")
    }

    @Test
    fun `applying command with invalid cmdId is rejected`() {
        val state = baseState()
        val cmd = AdvanceDay(cmdId = -1L)

        val result = canApply(state, cmd)

        assertTrue(result is ValidationResult.Rejected)
    }
}
