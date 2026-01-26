package test

import core.*
import core.hash.hashState
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.initialState
import test.helpers.Scenario
import test.helpers.assertAvailableCopper
import test.helpers.assertEventCount
import test.helpers.assertNoInvariantViolations
import test.helpers.assertNoRejections
import test.helpers.assertReplayDeterminism
import test.helpers.assertReservedCopper
import test.helpers.assertSingleRejection
import test.helpers.assertStateValid
import test.helpers.assertStepOk
import test.helpers.mainEventTypes
import test.helpers.printScenarioResult
import test.helpers.returns
import test.helpers.runScenario
import kotlin.test.*

@P1
class P1_015_GoldenReplaysTest {

    @Test
    fun `GR1 happy path full contract lifecycle`() {
        val scenario = Scenario(
            scenarioId = "GR1_happy_path",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L),
                AdvanceDay(cmdId = 3L),
                AdvanceDay(cmdId = 4L),
                SellTrophies(amount = 0, cmdId = 5L)
            ),
            description = "Happy path: post → take → resolve (auto-close as per outcome) → sell-all"
        )

        fun mainTypes(stepRes: StepResult): List<String> = mainEventTypes(stepRes.events)

        fun assertMainTypesContainsInOrder(actual: List<String>, requiredInOrder: List<String>, label: String) {
            var i = 0
            for (t in actual) {
                if (i < requiredInOrder.size && t == requiredInOrder[i]) i++
            }
            assertEquals(
                requiredInOrder.size,
                i,
                "$label: expected to contain in order=$requiredInOrder; actual=$actual"
            )
        }

        val result = runScenario(scenario)

        // Fix #1: per-step invariant preservation + no rejections + sequential seq (via assertStepOk)
        result.stepResults.forEachIndexed { idx, stepRes ->
            val label = "GR1 step ${idx + 1}"
            assertStepOk(stepRes.events, label)
            assertStateValid(stepRes.state, "$label: state must satisfy invariants")
        }

        // Global sanity (redundant but cheap and keeps failure location clearer)
        assertNoRejections(result.allEvents, "GR1: must have no rejections")
        assertNoInvariantViolations(result.allEvents, "GR1: must have no invariant violations")

        // Fix #2: stronger per-step sequence/content checks (low-risk: minimal expectations)
        run {
            val s1 = result.stepResults[0]
            val types = mainTypes(s1)
            assertTrue(types.isNotEmpty(), "GR1 step 1: must emit main events")
            assertMainTypesContainsInOrder(
                actual = types,
                requiredInOrder = listOf("DayStarted", "InboxGenerated"),
                label = "GR1 step 1"
            )
        }

        run {
            val s2 = result.stepResults[1]
            val types = mainTypes(s2)
            assertEventCount<ContractPosted>(
                s2.events,
                expected = 1,
                message = "GR1 step 2: must post exactly one contract"
            )
            assertTrue("ContractPosted" in types, "GR1 step 2: ContractPosted must be present; types=$types")

            val posted = s2.events.filterIsInstance<ContractPosted>().single()
            assertEquals(10, posted.fee, "GR1 step 2: ContractPosted.fee must be 10")
            assertEquals(SalvagePolicy.GUILD, posted.salvage, "GR1 step 2: ContractPosted.salvage must be GUILD")
        }

        run {
            val s3 = result.stepResults[2]
            assertEventCount<ContractTaken>(
                s3.events,
                expected = 1,
                message = "GR1 step 3: must take exactly one contract"
            )
        }

        run {
            val s4 = result.stepResults[3]
            assertEventCount<ContractResolved>(
                s4.events,
                expected = 1,
                message = "GR1 step 4: must resolve exactly one contract"
            )
            val resolved = s4.events.filterIsInstance<ContractResolved>().single()
            assertTrue(resolved.trophiesCount >= 0, "GR1 step 4: trophiesCount must be non-negative")
        }

        // Sell-all can be no-op; only assert it does not reject/violate (already covered by assertStepOk)
        run {
            val s5 = result.stepResults[4]
            val sold = s5.events.filterIsInstance<TrophySold>().size
            assertTrue(sold >= 0, "GR1 step 5: TrophySold count must be >= 0 (no-op allowed)")
        }

        // Final-state integrity (existing + Fix #6 aligned)
        assertEquals(3, result.finalState.meta.dayIndex, "GR1: should end on day 3")
        assertEquals(0, result.finalState.returns.count { it.requiresPlayerClose }, "GR1: no pending returns requiring close")
        assertStateValid(result.finalState, "GR1: final state must satisfy invariants")

        val finalStateHash = hashState(result.finalState)
        assertEquals(64, finalStateHash.length, "GR1: state hash must be 64 chars (SHA-256 hex)")

        // Optional: keep for local debugging (does not affect assertions)
        printScenarioResult(result)
    }

    @Test
    fun `GR2 rejection scenarios validation failures`() {
        // Fix #3: state-unchanged-on-rejection is asserted in every rejection scenario below.

        // 2a: Post contract with invalid inbox ID (NOT_FOUND)
        run {
            val rng = Rng(100L)
            val r1 = step(initialState(42u), AdvanceDay(cmdId = 1L), rng)
            val before = r1.state

            val r2 = step(before, PostContract(inboxId = 999L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L), rng)
            assertSingleRejection(r2.events, RejectReason.NOT_FOUND, "GR2a: expected NOT_FOUND")
            assertEquals(before, r2.state, "GR2a: rejection must not mutate state")
            assertNoInvariantViolations(r2.events, "GR2a: rejection must not emit invariant violations")
        }

        // 2b: Close return for non-existent active contract (NOT_FOUND)
        run {
            val rng = Rng(100L)
            val r1 = step(initialState(42u), AdvanceDay(cmdId = 1L), rng)
            val before = r1.state

            val r2 = step(before, CloseReturn(activeContractId = 999L, cmdId = 2L), rng)
            assertSingleRejection(r2.events, RejectReason.NOT_FOUND, "GR2b: expected NOT_FOUND")
            assertEquals(before, r2.state, "GR2b: rejection must not mutate state")
            assertNoInvariantViolations(r2.events, "GR2b: rejection must not emit invariant violations")
        }

        // 2c: Post contract with negative fee (INVALID_ARG)
        run {
            val rng = Rng(100L)
            val r1 = step(initialState(42u), AdvanceDay(cmdId = 1L), rng)
            val before = r1.state

            val r2 = step(before, PostContract(inboxId = 1L, fee = -5, salvage = SalvagePolicy.GUILD, cmdId = 2L), rng)
            assertSingleRejection(r2.events, RejectReason.INVALID_ARG, "GR2c: expected INVALID_ARG")
            assertEquals(before, r2.state, "GR2c: rejection must not mutate state")
            assertNoInvariantViolations(r2.events, "GR2c: rejection must not emit invariant violations")
        }
    }

    @Test
    fun `GR3 boundary cases insufficient money and escrow`() {
        // 3a: Fee exceeds available money (reject)
        run {
            val rng = Rng(100L)
            val r1 = step(initialState(42u), AdvanceDay(cmdId = 1L), rng)
            val before = r1.state

            val r2 = step(before, PostContract(inboxId = 1L, fee = 150, salvage = SalvagePolicy.GUILD, cmdId = 2L), rng)
            assertSingleRejection(
                r2.events,
                RejectReason.INVALID_STATE,
                "GR3a: expected INVALID_STATE (insufficient money)"
            )
            assertEquals(before, r2.state, "GR3a: rejection must not mutate state")
            assertNoInvariantViolations(r2.events, "GR3a: rejection must not emit invariant violations")
        }

        // 3b: Multiple posts exhaust available money; third is rejected; reserved accumulation verified
        run {
            val rng = Rng(100L)
            var state = initialState(42u)
            val allEvents = mutableListOf<Event>()

            val r1 = step(state, AdvanceDay(cmdId = 1L), rng)
            state = r1.state
            allEvents += r1.events

            val p1 = step(state, PostContract(inboxId = 1L, fee = 40, salvage = SalvagePolicy.GUILD, cmdId = 2L), rng)
            state = p1.state
            allEvents += p1.events

            val p2 = step(state, PostContract(inboxId = 2L, fee = 40, salvage = SalvagePolicy.GUILD, cmdId = 3L), rng)
            state = p2.state
            allEvents += p2.events

            val beforeThird = state
            val p3 = step(state, PostContract(inboxId = 3L, fee = 30, salvage = SalvagePolicy.GUILD, cmdId = 4L), rng)
            allEvents += p3.events

            assertSingleRejection(p3.events, RejectReason.INVALID_STATE, "GR3b: expected INVALID_STATE for third post")
            assertEventCount<ContractPosted>(allEvents, 2, "GR3b: expected exactly 2 successful ContractPosted total")
            assertEquals(beforeThird, p3.state, "GR3b: rejection must not mutate state")
            assertReservedCopper(p3.state, 80, "GR3b: reserved should be 40+40")
            assertAvailableCopper(p3.state, 20, "GR3b: available should be 100-80")
            assertNoInvariantViolations(p3.events, "GR3b: rejection must not emit invariant violations")
        }

        // 3c: Sell trophies when stock is zero (no-op)
        run {
            val rng = Rng(100L)
            val r1 = step(initialState(42u), AdvanceDay(cmdId = 1L), rng)
            val r2 = step(r1.state, SellTrophies(amount = 0, cmdId = 2L), rng)

            assertEventCount<TrophySold>(r2.events, 0, "GR3c: no TrophySold when stock is 0")
            assertNoRejections(r2.events, "GR3c: sell-all must not reject")
            assertNoInvariantViolations(r2.events, "GR3c: sell-all must not violate invariants")
        }
    }

    @Test
    fun `golden replay hashes are stable across runs`() {
        val scenario = Scenario(
            scenarioId = "GR_hash_stability",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L),
                AdvanceDay(cmdId = 3L)
            ),
            description = "Hash stability regression check"
        )
        assertReplayDeterminism(scenario)
    }

    @Test
    fun `golden replay RNG draw count is stable`() {
        val scenario = Scenario(
            scenarioId = "GR_rng_stability",
            stateSeed = 42u,
            rngSeed = 200L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                AdvanceDay(cmdId = 2L)
            ),
            description = "RNG draw count stability"
        )

        assertReplayDeterminism(scenario)
        printScenarioResult(runScenario(scenario))
    }
}
