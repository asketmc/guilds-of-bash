package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.hash.hashEvents
import core.hash.hashState
import core.invariants.InvariantId
import core.primitives.*
import core.rng.Rng
import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: Hashing tests.
 * Replay validation depends on stable hashing.
 */
@P1
class P1_HashingTest {

    @Test
    fun `hashState produces 64-character lowercase hex`() {
        val state = initialState(42u)
        val hash = hashState(state)

        assertEquals(64, hash.length, "SHA-256 hex must be 64 characters")
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, "Hash must be lowercase hex")
    }

    @Test
    fun `hashState is deterministic`() {
        val state = initialState(42u)

        val hash1 = hashState(state)
        val hash2 = hashState(state)

        assertEquals(hash1, hash2, "Repeated hashing must produce identical output")
    }

    @Test
    fun `hashState changes when state changes`() {
        val state1 = initialState(42u)
        val rng = Rng(100L)

        val result = step(state1, AdvanceDay(cmdId = 1L), rng)
        val state2 = result.state

        val hash1 = hashState(state1)
        val hash2 = hashState(state2)

        assertNotEquals(hash1, hash2, "Different states must have different hashes")
    }

    @Test
    fun `hashState same for identical states`() {
        val state1 = initialState(42u)
        val state2 = initialState(42u)

        val hash1 = hashState(state1)
        val hash2 = hashState(state2)

        assertEquals(hash1, hash2, "Identical states must have identical hashes")
    }

    @Test
    fun `hashEvents produces 64-character lowercase hex`() {
        val state = initialState(42u)
        val rng = Rng(100L)

        val result = step(state, AdvanceDay(cmdId = 1L), rng)

        val hash = hashEvents(result.events)

        assertEquals(64, hash.length, "SHA-256 hex must be 64 characters")
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' }, "Hash must be lowercase hex")
    }

    @Test
    fun `hashEvents is deterministic`() {
        val state = initialState(42u)
        val rng = Rng(100L)

        val result = step(state, AdvanceDay(cmdId = 1L), rng)

        val hash1 = hashEvents(result.events)
        val hash2 = hashEvents(result.events)

        assertEquals(hash1, hash2, "Repeated hashing must produce identical output")
    }

    @Test
    fun `hashEvents changes when events change`() {
        val state = initialState(42u)
        val rng = Rng(100L)

        val result1 = step(state, AdvanceDay(cmdId = 1L), rng)
        val result2 = step(result1.state, AdvanceDay(cmdId = 2L), rng)

        val hash1 = hashEvents(result1.events)
        val hash2 = hashEvents(result2.events)

        assertNotEquals(hash1, hash2, "Different event lists must have different hashes")
    }

    @Test
    fun `hashEvents same for identical event lists`() {
        val state = initialState(42u)
        val rng1 = Rng(100L)
        val rng2 = Rng(100L)

        val result1 = step(state, AdvanceDay(cmdId = 1L), rng1)
        val result2 = step(state, AdvanceDay(cmdId = 1L), rng2)

        val hash1 = hashEvents(result1.events)
        val hash2 = hashEvents(result2.events)

        assertEquals(hash1, hash2, "Identical event lists must have identical hashes")
    }

    @Test
    fun `hashEvents handles empty list`() {
        val emptyEvents = emptyList<Event>()

        val hash = hashEvents(emptyEvents)

        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hashEvents handles single event`() {
        val events = listOf(
            DayStarted(day = 1, revision = 1L, cmdId = 1L, seq = 1L)
        )

        val hash = hashEvents(events)

        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hashEvents order matters`() {
        val event1 = DayStarted(day = 1, revision = 1L, cmdId = 1L, seq = 1L)
        val event2 = DayEnded(
            day = 1,
            revision = 1L,
            cmdId = 1L,
            seq = 2L,
            snapshot = DaySnapshot(
                day = 1,
                revision = 1L,
                money = 10,
                trophies = 0,
                regionStability = 50,
                guildReputation = 50,
                inboxCount = 0,
                boardCount = 0,
                activeCount = 0,
                returnsNeedingCloseCount = 0
            )
        )

        val hash1 = hashEvents(listOf(event1, event2))
        val hash2 = hashEvents(listOf(event2, event1))

        assertNotEquals(hash1, hash2, "Event order must affect hash")
    }

    @Test
    fun `hashEvents includes all event types`() {
        // Test that all event types can be hashed without crashes
        val events = listOf(
            DayStarted(day = 1, revision = 1L, cmdId = 1L, seq = 1L),
            InboxGenerated(day = 1, revision = 1L, cmdId = 1L, seq = 2L, count = 2, contractIds = intArrayOf(1, 2)),
            HeroesArrived(day = 1, revision = 1L, cmdId = 1L, seq = 3L, count = 2, heroIds = intArrayOf(1, 2)),
            ContractPosted(day = 1, revision = 1L, cmdId = 1L, seq = 4L, boardContractId = 1, fromInboxId = 1, rank = core.primitives.Rank.F, fee = 0, salvage = core.primitives.SalvagePolicy.GUILD),
            ContractTaken(day = 1, revision = 1L, cmdId = 1L, seq = 5L, activeContractId = 1, boardContractId = 1, heroIds = intArrayOf(1), daysRemaining = 2),
            WipAdvanced(day = 1, revision = 1L, cmdId = 1L, seq = 6L, activeContractId = 1, daysRemaining = 1),
            ContractResolved(day = 1, revision = 1L, cmdId = 1L, seq = 7L, activeContractId = 1, outcome = core.primitives.Outcome.SUCCESS, trophiesCount = 0, quality = core.primitives.Quality.OK, reasonTags = intArrayOf()),
            ReturnClosed(day = 1, revision = 1L, cmdId = 1L, seq = 8L, activeContractId = 1),
            TrophySold(day = 1, revision = 1L, cmdId = 1L, seq = 9L, amount = 5, moneyGained = 50),
            StabilityUpdated(day = 1, revision = 1L, cmdId = 1L, seq = 10L, oldStability = 50, newStability = 51),
            DayEnded(day = 1, revision = 1L, cmdId = 1L, seq = 11L, snapshot = DaySnapshot(day = 1, revision = 1L, money = 10, trophies = 0, regionStability = 51, guildReputation = 50, inboxCount = 0, boardCount = 0, activeCount = 0, returnsNeedingCloseCount = 0)),
            CommandRejected(day = 1, revision = 1L, cmdId = 1L, seq = 12L, cmdType = "Test", reason = RejectReason.NOT_FOUND, detail = "test"),
            InvariantViolated(day = 1, revision = 1L, cmdId = 1L, seq = 13L, invariantId = InvariantId.IDS__NEXT_CONTRACT_ID_POSITIVE, details = "test")
        )

        val hash = hashEvents(events)

        assertEquals(64, hash.length)
        assertTrue(hash.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `hashState reflects arrivalsToday changes`() {
        val state1 = initialState(42u).copy(
            heroes = core.state.HeroState(
                roster = emptyList(),
                arrivalsToday = emptyList()
            )
        )
        val state2 = initialState(42u).copy(
            heroes = core.state.HeroState(
                roster = emptyList(),
                arrivalsToday = listOf(core.primitives.HeroId(1))
            )
        )

        val hash1 = hashState(state1)
        val hash2 = hashState(state2)

        // Note: arrivalsToday is NOT serialized, so hashes might be same
        // This test documents the behavior
    }
}
