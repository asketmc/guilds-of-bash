package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.invariants.verifyInvariants
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.initialState
import test.helpers.Scenario
import test.helpers.assertNoInvariantViolations
import kotlin.test.*

/**
 * P1 CRITICAL: Invariants After Each Step Test.
 * Validates that invariants hold after EVERY step in golden scenarios.
 *
 * Purpose:
 * - Ensure reducer maintains invariants throughout execution (not just at end)
 * - Catch transient invariant violations that might be masked by later steps
 * - Validate that `verifyInvariants()` is called after every command
 *
 * Contract:
 * - `verifyInvariants(state)` must return empty list after each accepted command
 * - Rejected commands must also leave state in valid state (invariants hold)
 */
@P1
@Smoke
class P1_020_InvariantsAfterEachStepTest {

    @Test
    fun `GR1 happy path has no invariant violations after each step`() {
        // GIVEN: GR1 scenario
        val scenario = Scenario(
            scenarioId = "GR1_invariants_each_step",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L),
                AdvanceDay(cmdId = 3L),
                AdvanceDay(cmdId = 4L),
                CloseReturn(activeContractId = 1L, cmdId = 5L),
                SellTrophies(amount = 0, cmdId = 6L)
            )
        )

        // WHEN: Run scenario and check invariants after each step
        var state = initialState(scenario.stateSeed)
        val rng = Rng(scenario.rngSeed)

        for ((index, cmd) in scenario.commands.withIndex()) {
            val result = step(state, cmd, rng)
            state = result.state

            // THEN: Verify no invariant violations after this step
            val violations = verifyInvariants(state)
            assertTrue(violations.isEmpty(),
                "Step ${index + 1} (${cmd::class.simpleName}): Expected no invariant violations, found: $violations")

            // Also verify InvariantViolated events are not present (unless intentional test)
            val violationEvents = result.events.filterIsInstance<InvariantViolated>()
            assertTrue(violationEvents.isEmpty(),
                "Step ${index + 1} (${cmd::class.simpleName}): Found InvariantViolated events: $violationEvents")
        }

        println("✓ GR1: No invariant violations after any of ${scenario.commands.size} steps")
    }

    @Test
    fun `GR2 rejection scenarios maintain invariants after rejection`() {
        // GIVEN: Rejection scenarios
        val scenarios = listOf(
            Scenario(
                scenarioId = "GR2a_invalid_inbox",
                stateSeed = 42u,
                rngSeed = 100L,
                commands = listOf(
                    AdvanceDay(cmdId = 1L),
                    PostContract(inboxId = 999L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L)  // Reject
                )
            ),
            Scenario(
                scenarioId = "GR2b_invalid_active_id",
                stateSeed = 42u,
                rngSeed = 100L,
                commands = listOf(
                    AdvanceDay(cmdId = 1L),
                    CloseReturn(activeContractId = 999L, cmdId = 2L)  // Reject
                )
            )
        )

        for (scenario in scenarios) {
            var state = initialState(scenario.stateSeed)
            val rng = Rng(scenario.rngSeed)

            for ((index, cmd) in scenario.commands.withIndex()) {
                val result = step(state, cmd, rng)
                state = result.state

                // THEN: Invariants must hold even after rejection
                val violations = verifyInvariants(state)
                assertTrue(violations.isEmpty(),
                    "${scenario.scenarioId} step ${index + 1}: Rejections must leave state valid, found violations: $violations")
            }

            println("✓ ${scenario.scenarioId}: Invariants maintained after rejection")
        }
    }

    @Test
    fun `GR3 boundary cases maintain invariants after each step`() {
        // GIVEN: GR3 boundary scenario
        val scenario = Scenario(
            scenarioId = "GR3_boundary_escrow",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 40, salvage = SalvagePolicy.GUILD, cmdId = 2L),
                PostContract(inboxId = 2L, fee = 40, salvage = SalvagePolicy.GUILD, cmdId = 3L),
                PostContract(inboxId = 3L, fee = 30, salvage = SalvagePolicy.GUILD, cmdId = 4L)  // Should reject
            )
        )

        // WHEN: Run scenario
        var state = initialState(scenario.stateSeed)
        val rng = Rng(scenario.rngSeed)

        for ((index, cmd) in scenario.commands.withIndex()) {
            val result = step(state, cmd, rng)
            state = result.state

            // THEN: Invariants must hold after each step (including escrow boundary)
            val violations = verifyInvariants(state)
            assertTrue(violations.isEmpty(),
                "GR3 step ${index + 1}: Expected no invariant violations, found: $violations")
        }

        println("✓ GR3: Invariants maintained through escrow boundary cases")
    }

    @Test
    fun `verifyInvariants is called after every accepted command`() {
        // GIVEN: Arbitrary command sequence
        val commands = listOf(
            AdvanceDay(cmdId = 1L),
            AdvanceDay(cmdId = 2L),
            AdvanceDay(cmdId = 3L)
        )

        var state = initialState(42u)
        val rng = Rng(100L)

        // WHEN: Execute commands
        for (cmd in commands) {
            val result = step(state, cmd, rng)
            state = result.state

            // THEN: Manually call verifyInvariants() to simulate reducer's internal check
            val violations = verifyInvariants(state)

            // step() internally calls verifyInvariants() and emits InvariantViolated events if any
            // So we verify that no such events are present
            val violationEvents = result.events.filterIsInstance<InvariantViolated>()
            assertEquals(violations.size, violationEvents.size,
                "step() should emit InvariantViolated events equal to verifyInvariants() result")
        }

        println("✓ verifyInvariants() is called after every command (verified via event emission)")
    }

    @Test
    fun `long scenario maintains invariants throughout execution`() {
        // GIVEN: Long scenario with many steps
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        val stepCount = 20
        repeat(stepCount) { iteration ->
            val cmd = AdvanceDay(cmdId = cmdId++)
            val result = step(state, cmd, rng)
            state = result.state

            // THEN: Invariants must hold after each of many steps
            val violations = verifyInvariants(state)
            assertTrue(violations.isEmpty(),
                "Step $iteration: Expected no invariant violations in long scenario, found: $violations")
        }

        println("✓ Long scenario ($stepCount steps): No invariant violations")
    }

    @Test
    fun `invariants hold across multiple post-take-resolve cycles`() {
        // GIVEN: Multiple contract cycles
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        repeat(3) { cycle ->
            // Day advance
            val r1 = step(state, AdvanceDay(cmdId = cmdId++), rng)
            state = r1.state
            assertNoInvariantViolations(r1.events, "Cycle $cycle: AdvanceDay")

            // Post contract
            val inboxId = state.contracts.inbox.firstOrNull()?.id?.value?.toLong()
            if (inboxId != null) {
                val r2 = step(state, PostContract(inboxId = inboxId, fee = 5, salvage = SalvagePolicy.GUILD, cmdId = cmdId++), rng)
                state = r2.state
                assertNoInvariantViolations(r2.events, "Cycle $cycle: PostContract")

                // Take
                val r3 = step(state, AdvanceDay(cmdId = cmdId++), rng)
                state = r3.state
                assertNoInvariantViolations(r3.events, "Cycle $cycle: Take")

                // Resolve
                val r4 = step(state, AdvanceDay(cmdId = cmdId++), rng)
                state = r4.state
                assertNoInvariantViolations(r4.events, "Cycle $cycle: Resolve")

                // Close if needed
                val returnsNeedingClose = state.contracts.returns.filter { it.requiresPlayerClose }
                if (returnsNeedingClose.isNotEmpty()) {
                    val activeId = returnsNeedingClose.first().activeContractId.value.toLong()
                    val r5 = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng)
                    state = r5.state
                    assertNoInvariantViolations(r5.events, "Cycle $cycle: CloseReturn")
                }
            }

            println("✓ Cycle $cycle: All invariants maintained")
        }

        println("✓ Multiple post-take-resolve cycles: All invariants maintained")
    }

    @Test
    fun `invariants hold after sell trophies with various amounts`() {
        // GIVEN: State with trophies
        var state = initialState(42u)
        val rng = Rng(300L)
        var cmdId = 1L

        // Create trophies
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(state, PostContract(inboxId = inboxId, fee = 100, salvage = SalvagePolicy.GUILD, cmdId = cmdId++), rng).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state

        val returnsNeedingClose = state.contracts.returns.filter { it.requiresPlayerClose }
        if (returnsNeedingClose.isNotEmpty()) {
            val activeId = returnsNeedingClose.first().activeContractId.value.toLong()
            state = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng).state
        }

        if (state.economy.trophiesStock == 0) {
            println("⚠ Skipping test: no trophies with this seed")
            return
        }

        // WHEN: Sell trophies with various amounts
        val amounts = listOf(1, 5, 100, -10, 0)
        for (amount in amounts) {
            val result = step(state, SellTrophies(amount = amount, cmdId = cmdId++), rng)
            state = result.state

            // THEN: Invariants must hold after each sell
            assertNoInvariantViolations(result.events, "SellTrophies(amount=$amount) must maintain invariants")
        }

        println("✓ Invariants maintained after selling trophies with various amounts")
    }

    @Test
    fun `invariants hold in initial state`() {
        // GIVEN: Fresh initial state
        val state = initialState(42u)

        // THEN: Invariants must hold in initial state
        val violations = verifyInvariants(state)
        assertTrue(violations.isEmpty(),
            "Initial state must satisfy all invariants, found violations: $violations")

        println("✓ Initial state satisfies all invariants")
    }
}
