package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: GameState initialization tests.
 */
class P1_012_GameStateInitializationTest {

    @Test
    fun `initial state has expected defaults`() {
        val state = initialState(42u)

        assertEquals(100, state.economy.moneyCopper)
        assertEquals(0, state.economy.trophiesStock)
        assertTrue(state.contracts.inbox.isEmpty())
        assertTrue(state.heroes.roster.isEmpty())
    }
}
