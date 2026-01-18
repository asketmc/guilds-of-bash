package test

import core.*
import core.primitives.*
import core.rng.Rng
import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: Reducer core game loop tests.
 * Any failure here breaks the entire game.
 */
class P1_ReducerCriticalTest {

    @Test
    fun `step with valid command increments revision exactly once`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(1L, result.state.meta.revision, "Revision should increment by 1")
        assertEquals(0L, state.meta.revision, "Original state unchanged")
    }

    @Test
    fun `step with rejected command does not change state`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        // CloseReturn on non-existent active contract
        val cmd = CloseReturn(cmdId = 1L, activeContractId = ActiveContractId(999))

        val result = step(state, cmd, rng)

        assertEquals(state, result.state, "State must not change on rejection")
        assertEquals(1, result.events.size, "Should emit exactly 1 CommandRejected event")
        assertTrue(result.events[0] is CommandRejected)
    }

    @Test
    fun `step always emits at least one event`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        assertTrue(result.events.isNotEmpty(), "Must emit at least one event")
    }

    @Test
    fun `step assigns sequential seq numbers starting at 1`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        result.events.forEachIndexed { index, event ->
            assertEquals((index + 1).toLong(), event.seq, "seq must be sequential starting at 1")
        }
    }

    @Test
    fun `AdvanceDay increments dayIndex`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(1, result.state.meta.dayIndex, "dayIndex should increment")
    }

    @Test
    fun `AdvanceDay event order is DayStarted InboxGenerated HeroesArrived DayEnded`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        // Filter out InvariantViolated events (if any)
        val mainEvents = result.events.filterNot { it is InvariantViolated }
        
        assertTrue(mainEvents.size >= 4, "Should have at least 4 main events")
        assertTrue(mainEvents[0] is DayStarted, "First event must be DayStarted")
        assertTrue(mainEvents[1] is InboxGenerated, "Second event must be InboxGenerated")
        assertTrue(mainEvents[2] is HeroesArrived, "Third event must be HeroesArrived")
        assertTrue(mainEvents.last() is DayEnded, "Last event must be DayEnded")
    }

    @Test
    fun `AdvanceDay generates exactly 2 inbox contracts`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(2, result.state.contracts.inbox.size, "Should generate 2 inbox contracts")
        
        val inboxGenEvent = result.events.filterIsInstance<InboxGenerated>().first()
        assertEquals(2, inboxGenEvent.count)
        assertEquals(2, inboxGenEvent.contractIds.size)
    }

    @Test
    fun `AdvanceDay generates exactly 2 heroes`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(2, result.state.heroes.roster.size, "Should generate 2 heroes")
        
        val heroesEvent = result.events.filterIsInstance<HeroesArrived>().first()
        assertEquals(2, heroesEvent.count)
        assertEquals(2, heroesEvent.heroIds.size)
    }

    @Test
    fun `AdvanceDay clears arrivalsToday before adding new arrivals`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        
        // First day
        val result1 = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result1.state
        
        assertEquals(2, state.heroes.arrivalsToday.size, "Day 1: should have 2 arrivals")
        
        // Second day
        val result2 = step(state, AdvanceDay(cmdId = 2L), rng)
        state = result2.state
        
        assertEquals(2, state.heroes.arrivalsToday.size, "Day 2: should have 2 NEW arrivals (not 4)")
    }

    @Test
    fun `AdvanceDay increments ID counters correctly`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(3, result.state.meta.ids.nextContractId, "Should be 1 + 2 inbox contracts")
        assertEquals(3, result.state.meta.ids.nextHeroId, "Should be 1 + 2 heroes")
    }

    @Test
    fun `InvariantViolated events are inserted before DayEnded`() {
        // Create a state that will violate invariants (negative money)
        var state = initialState(42u).copy(
            economy = core.state.EconomyState(moneyCopper = -100, trophiesStock = 0)
        )
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        val result = step(state, cmd, rng)

        val dayEndedIndex = result.events.indexOfLast { it is DayEnded }
        val violationIndices = result.events.indices.filter { result.events[it] is InvariantViolated }
        
        if (violationIndices.isNotEmpty()) {
            assertTrue(
                violationIndices.all { it < dayEndedIndex },
                "All InvariantViolated events must come before DayEnded"
            )
        }
    }

    @Test
    fun `PostContract validation works`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        
        // Try to post a non-existent inbox contract
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = ContractId(999),
            rank = Rank.F,
            fee = 100,
            salvage = SalvagePolicy.GUILD
        )

        val result = step(state, cmd, rng)

        assertTrue(result.events.first() is CommandRejected, "Should reject non-existent inbox")
        assertEquals(state, result.state, "State unchanged on rejection")
    }

    @Test
    fun `CloseReturn validation works`() {
        val state = initialState(42u)
        val rng = Rng(100L)
        
        // Try to close a non-existent return
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = ActiveContractId(999)
        )

        val result = step(state, cmd, rng)

        assertTrue(result.events.first() is CommandRejected, "Should reject non-existent return")
        assertEquals(state, result.state, "State unchanged on rejection")
    }
}
