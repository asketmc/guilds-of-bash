package test

import core.AdvanceDay
import core.rng.Rng
import core.serde.deserialize
import core.serde.serialize
import core.state.initialState
import core.step
import kotlin.test.*

/**
 * P1 CRITICAL: Serialization/deserialization tests.
 * Save/load corruption breaks the entire game.
 */
class P1_SerializationTest {

    @Test
    fun `serialize produces non-empty JSON`() {
        val state = initialState(42u)

        val json = serialize(state)

        assertTrue(json.isNotEmpty(), "JSON must not be empty")
        assertTrue(json.startsWith("{"), "JSON must start with {")
        assertTrue(json.endsWith("}"), "JSON must end with }")
    }

    @Test
    fun `serialize is deterministic`() {
        val state = initialState(42u)

        val json1 = serialize(state)
        val json2 = serialize(state)

        assertEquals(json1, json2, "Repeated serialization must produce identical output")
    }

    @Test
    fun `deserialize round-trip preserves state (except arrivalsToday)`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        
        // Advance a day to get some data
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result.state.copy(
            heroes = result.state.heroes.copy(arrivalsToday = emptyList())
        )

        val json = serialize(state)
        val restored = deserialize(json)

        assertEquals(state, restored, "Round-trip must preserve state")
    }

    @Test
    fun `deserialize sets arrivalsToday to empty`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        
        // Advance a day to get arrivals
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result.state

        assertTrue(state.heroes.arrivalsToday.isNotEmpty(), "State should have arrivals before save")

        val json = serialize(state)
        val restored = deserialize(json)

        assertTrue(restored.heroes.arrivalsToday.isEmpty(), "arrivalsToday must be empty after load")
    }

    @Test
    fun `deserialize validates saveVersion`() {
        val invalidJson = """{"meta":{"saveVersion":999,"seed":42,"dayIndex":0,"revision":0,"ids":{"nextContractId":1,"nextHeroId":1,"nextActiveContractId":1}},"guild":{"guildRank":1,"reputation":50},"region":{"stability":50},"economy":{"moneyCopper":100,"trophiesStock":0,"reservedCopper":0},"contracts":{"inbox":[],"board":[],"active":[],"returns":[]},"heroes":{"roster":[]}}"""

        val exception = assertFails {
            deserialize(invalidJson)
        }

        assertTrue(exception is IllegalArgumentException, "Must throw IllegalArgumentException")
        assertTrue(exception.message!!.contains("saveVersion"), "Error message must mention saveVersion")
    }

    @Test
    fun `serialize preserves value classes as raw int`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        
        // Create some contracts and heroes
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result.state

        val json = serialize(state)

        // Check that IDs are serialized as numbers, not objects
        assertFalse(json.contains("\"value\""), "Value classes should be raw ints, not {\"value\":N}")
        assertTrue(json.contains("\"id\":1"), "Should contain raw ID numbers")
    }

    @Test
    fun `serialize produces compact JSON (no pretty print)`() {
        val state = initialState(42u)

        val json = serialize(state)

        assertFalse(json.contains("\n"), "Compact JSON must not contain newlines")
        assertFalse(json.contains("  "), "Compact JSON must not contain indentation")
    }

    @Test
    fun `deserialize handles empty collections`() {
        val state = initialState(42u)

        val json = serialize(state)
        val restored = deserialize(json)

        assertTrue(restored.contracts.inbox.isEmpty())
        assertTrue(restored.contracts.board.isEmpty())
        assertTrue(restored.contracts.active.isEmpty())
        assertTrue(restored.contracts.returns.isEmpty())
        assertTrue(restored.heroes.roster.isEmpty())
        assertTrue(restored.heroes.arrivalsToday.isEmpty())
    }

    @Test
    fun `deserialize handles populated collections`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        
        // Advance multiple days to get diverse data
        for (i in 1..3) {
            val result = step(state, AdvanceDay(cmdId = i.toLong()), rng)
            state = result.state
        }

        // Clear arrivalsToday for clean comparison
        state = state.copy(heroes = state.heroes.copy(arrivalsToday = emptyList()))

        val json = serialize(state)
        val restored = deserialize(json)

        assertEquals(state.contracts.inbox.size, restored.contracts.inbox.size)
        assertEquals(state.heroes.roster.size, restored.heroes.roster.size)
        assertEquals(state, restored)
    }

    @Test
    fun `serialize handles all enum types correctly`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        
        // Create diverse state with different enums
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result.state

        val json = serialize(state)
        val restored = deserialize(json)

        // Verify enums survived round-trip
        if (restored.heroes.roster.isNotEmpty()) {
            assertEquals(state.heroes.roster[0].rank, restored.heroes.roster[0].rank)
            assertEquals(state.heroes.roster[0].klass, restored.heroes.roster[0].klass)
            assertEquals(state.heroes.roster[0].status, restored.heroes.roster[0].status)
        }
    }

    @Test
    fun `deserialize rejects malformed JSON`() {
        val malformedJson = "{invalid json"

        assertFails {
            deserialize(malformedJson)
        }
    }

    @Test
    fun `deserialize rejects incomplete JSON`() {
        val incompleteJson = """{"meta":{"saveVersion":1}}"""

        assertFails {
            deserialize(incompleteJson)
        }
    }
}
