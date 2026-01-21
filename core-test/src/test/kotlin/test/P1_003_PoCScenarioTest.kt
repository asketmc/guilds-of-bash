// FILE: core-test/src/test/kotlin/test/P1_003_PoCScenarioTest.kt
package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.hash.hashState
import core.invariants.verifyInvariants
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: PoC end-to-end scenario tests.
 * Validates the complete micro-game loop from contracts to trophies to money.
 */
class P1_003_PoCScenarioTest {

    @Test
    fun `poc scenario basic contract flow end to end`() {
        // GIVEN
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Initial state verification (reflects current initialization)
        assertEquals(100, state.economy.moneyCopper, "Initial money should be 100")
        assertEquals(0, state.economy.trophiesStock, "Initial trophies should be 0")
        assertEquals(50, state.region.stability, "Initial stability should be 50")
        assertEquals(2, state.contracts.inbox.size, "Initial inbox count is seeded by initialState()")

        // Helper: enforce no rejections / no invariant violations for a step
        fun assertStepOk(events: List<Event>, label: String) {
            val inv = events.filterIsInstance<InvariantViolated>()
            assertTrue(inv.isEmpty(), "$label: must not emit InvariantViolated, got=$inv")

            val rej = events.filterIsInstance<CommandRejected>()
            assertTrue(rej.isEmpty(), "$label: must not reject, got=$rej")

            // seq must be 1..N within a step (exclude no events case)
            if (events.isNotEmpty()) {
                events.forEachIndexed { index, e ->
                    assertEquals((index + 1).toLong(), e.seq, "$label: seq must be sequential")
                }
            }
        }

        // Step 1: AdvanceDay (should emit the day envelope events)
        val r1 = step(state, AdvanceDay(cmdId = cmdId++), rng)
        assertStepOk(r1.events, "AdvanceDay#1")
        state = r1.state

        assertTrue(r1.events.any { it is DayStarted }, "AdvanceDay#1 must emit DayStarted")
        assertTrue(r1.events.any { it is InboxGenerated }, "AdvanceDay#1 must emit InboxGenerated")
        assertTrue(r1.events.any { it is HeroesArrived }, "AdvanceDay#1 must emit HeroesArrived")
        assertTrue(r1.events.any { it is DayEnded }, "AdvanceDay#1 must emit DayEnded")

        // Step 2: PostContract from inbox
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        val inboxBefore = state.contracts.inbox.size
        val boardBefore = state.contracts.board.size

        val r2 = step(
            state,
            PostContract(
                inboxId = inboxId,
                fee = 10,
                salvage = SalvagePolicy.HERO, // generally increases hero attractiveness; still OK if logic differs
                cmdId = cmdId++
            ),
            rng
        )
        assertStepOk(r2.events, "PostContract")
        state = r2.state

        assertTrue(r2.events.any { it is ContractPosted }, "PostContract must emit ContractPosted")
        assertEquals(inboxBefore - 1, state.contracts.inbox.size, "Posted draft must be removed from inbox")
        assertEquals(boardBefore + 1, state.contracts.board.size, "Posted draft must be added to board")

        // Step 3+: AdvanceDay until we see at least one resolution (bounded, deterministic)
        // Some seeds/logic may require multiple ticks (take + WIP + resolve).
        var resolvedSeen = false
        repeat(6) { i ->
            val r = step(state, AdvanceDay(cmdId = cmdId++), rng)
            assertStepOk(r.events, "AdvanceDay#${i + 2}")
            state = r.state

            if (r.events.any { it is ContractResolved }) {
                resolvedSeen = true
                return@repeat
            }
        }
        assertTrue(resolvedSeen, "Expected to observe ContractResolved within bounded AdvanceDay iterations")

        // Final invariants + deterministic hash shape check
        val violations = verifyInvariants(state)
        assertTrue(violations.isEmpty(), "Final state must satisfy invariants, got=$violations")

        val h = hashState(state)
        assertEquals(64, h.length, "hashState must return 64 hex chars")
        assertTrue(h.all { it in '0'..'9' || it in 'a'..'f' }, "hashState must be lowercase hex")
    }
}
