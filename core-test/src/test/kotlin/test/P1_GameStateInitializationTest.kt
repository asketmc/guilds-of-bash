package test

import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: GameStateInitialization tests.
 * Invalid initial state = crash on game start.
 */
class P1_GameStateInitializationTest {

    @Test
    fun `initialState creates valid starting state`() {
        val state = initialState(42u)

        assertNotNull(state)
        assertEquals(1, state.meta.saveVersion, "Save version must be 1")
        assertEquals(42u, state.meta.seed, "Seed must match input")
        assertEquals(0, state.meta.dayIndex, "Game starts at day 0")
        assertEquals(0L, state.meta.revision, "Game starts at revision 0")
    }

    @Test
    fun `initialState has positive ID counters`() {
        val state = initialState(42u)

        assertTrue(state.meta.ids.nextContractId > 0, "nextContractId must be positive")
        assertTrue(state.meta.ids.nextHeroId > 0, "nextHeroId must be positive")
        assertTrue(state.meta.ids.nextActiveContractId > 0, "nextActiveContractId must be positive")
    }

    @Test
    fun `initialState has valid economy values`() {
        val state = initialState(42u)

        assertTrue(state.economy.moneyCopper >= 0, "Starting money must be non-negative")
        assertTrue(state.economy.trophiesStock >= 0, "Starting trophies must be non-negative")
    }

    @Test
    fun `initialState has valid guild values`() {
        val state = initialState(42u)

        assertTrue(state.guild.guildRank > 0, "Guild rank must be positive")
        assertTrue(state.guild.reputation in 0..100, "Reputation must be in 0..100")
    }

    @Test
    fun `initialState has valid region values`() {
        val state = initialState(42u)

        assertTrue(state.region.stability in 0..100, "Stability must be in 0..100")
    }

    @Test
    fun `initialState has empty collections`() {
        val state = initialState(42u)

        assertTrue(state.contracts.inbox.isEmpty(), "Inbox should start empty")
        assertTrue(state.contracts.board.isEmpty(), "Board should start empty")
        assertTrue(state.contracts.active.isEmpty(), "Active contracts should start empty")
        assertTrue(state.contracts.returns.isEmpty(), "Returns should start empty")
        assertTrue(state.heroes.roster.isEmpty(), "Hero roster should start empty")
        assertTrue(state.heroes.arrivalsToday.isEmpty(), "Arrivals should start empty")
    }

    @Test
    fun `initialState with different seeds are equal except seed`() {
        val state1 = initialState(1u)
        val state2 = initialState(2u)

        assertEquals(state1.meta.saveVersion, state2.meta.saveVersion)
        assertEquals(state1.meta.dayIndex, state2.meta.dayIndex)
        assertEquals(state1.meta.revision, state2.meta.revision)
        assertEquals(state1.meta.ids, state2.meta.ids)
        assertEquals(state1.guild, state2.guild)
        assertEquals(state1.region, state2.region)
        assertEquals(state1.economy, state2.economy)
        assertEquals(state1.contracts, state2.contracts)
        assertEquals(state1.heroes, state2.heroes)
    }

    @Test
    fun `initialState is deterministic for same seed`() {
        val state1 = initialState(42u)
        val state2 = initialState(42u)

        assertEquals(state1, state2, "Same seed must produce identical state")
    }
}
