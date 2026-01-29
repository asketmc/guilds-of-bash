package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.state.initialState
import kotlin.test.*

/**
 * P0 CRITICAL: GameState initialization tests.
 * Core startup functionality - must pass for app to start.
 */
@P0
@Smoke
class GameStateInitializationTest {

    @Test
    fun `initial state has expected defaults`() {
        val state = initialState(42u)

        assertEquals(100, state.economy.moneyCopper)
        assertEquals(0, state.economy.trophiesStock)
        // initialState currently seeds two inbox drafts
        assertEquals(2, state.contracts.inbox.size)
        assertTrue(state.heroes.roster.isEmpty())
    }
}
