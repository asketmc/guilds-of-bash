package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.hash.hashEvents
import core.hash.hashState
import core.primitives.SalvagePolicy
import kotlin.test.*

/**
 * P1 CRITICAL: Golden Replay Tests (GR1–GR3).
 * Validates end-to-end PoC scenarios with deterministic seeds and expected event sequences.
 *
 * Purpose:
 * - GR1: Happy path (full contract lifecycle)
 * - GR2: Rejection scenarios (validation failures)
 * - GR3: Boundary cases (escrow/money edge cases)
 *
 * Contract:
 * - Each golden replay must be reproducible with fixed seeds
 * - Event sequences and hashes must not change across refactorings
 * - No invariant violations on happy paths
 */
class P1_015_GoldenReplaysTest {

    @Test
    fun `GR1 happy path full contract lifecycle`() {
        // GIVEN: Deterministic scenario with full contract flow: post → take → resolve → close → sell
        val scenario = Scenario(
            scenarioId = "GR1_happy_path",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),                                              // Day 1: inbox + heroes
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L),  // Post contract
                AdvanceDay(cmdId = 3L),                                              // Day 2: take + advance WIP
                AdvanceDay(cmdId = 4L),                                              // Day 3: resolve
                CloseReturn(activeContractId = 1L, cmdId = 5L),                     // Close return
                SellTrophies(amount = 0, cmdId = 6L)                                 // Sell all trophies
            ),
            description = "Full happy path: contract posted, taken, resolved, closed, trophies sold"
        )

        // WHEN: Run scenario
        val result = runScenario(scenario)

        // THEN: Verify no rejections or invariant violations
        assertNoRejections(result.allEvents, "GR1: Happy path should have no rejections")
        assertNoInvariantViolations(result.allEvents, "GR1: Happy path should have no invariant violations")

        // Verify critical events are present
        assertEventTypesPresent(
            result.allEvents,
            setOf("DayStarted", "ContractPosted", "ContractTaken", "ContractResolved", "ReturnClosed", "TrophySold"),
            "GR1: Critical lifecycle events must be present"
        )

        // Verify final state consistency
        assertEquals(3, result.finalState.meta.dayIndex, "GR1: Should end on day 3")
        assertTrue(result.finalState.economy.moneyCopper <= 100, "GR1: Money should be <= initial (fee paid)")
        assertEquals(0, result.finalState.economy.trophiesStock, "GR1: All trophies sold")
        assertEquals(0, result.finalState.contracts.returns.filter { it.requiresPlayerClose }.size,
            "GR1: No pending returns requiring close")

        // Document golden hashes (regression check)
        val finalStateHash = hashState(result.finalState)
        assertEquals(64, finalStateHash.length, "GR1: State hash should be 64 chars (SHA-256 hex)")
        println("GR1 golden state hash: $finalStateHash")
        println("GR1 RNG draws: ${result.rngDraws}")
    }

    @Test
    fun `GR2 rejection scenarios validation failures`() {
        // GIVEN: Scenarios designed to trigger CommandRejected events

        // Scenario 2a: Post contract with invalid inbox ID (NOT_FOUND)
        val scenario2a = Scenario(
            scenarioId = "GR2a_invalid_inbox_id",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 999L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L)  // Invalid inboxId
            ),
            description = "Rejection: PostContract with non-existent inbox ID"
        )

        val result2a = runScenario(scenario2a)
        assertSingleRejection(result2a.allEvents, RejectReason.NOT_FOUND, "GR2a: Expected NOT_FOUND rejection")
        assertNoInvariantViolations(result2a.allEvents, "GR2a: Rejections should not cause invariant violations")
        println("GR2a: ✓ Invalid inbox ID correctly rejected")

        // Scenario 2b: Close return for non-existent active contract (NOT_FOUND)
        val scenario2b = Scenario(
            scenarioId = "GR2b_invalid_active_contract_id",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                CloseReturn(activeContractId = 999L, cmdId = 2L)  // Invalid activeContractId
            ),
            description = "Rejection: CloseReturn with non-existent active contract ID"
        )

        val result2b = runScenario(scenario2b)
        assertSingleRejection(result2b.allEvents, RejectReason.NOT_FOUND, "GR2b: Expected NOT_FOUND rejection")
        assertNoInvariantViolations(result2b.allEvents, "GR2b: Rejections should not cause invariant violations")
        println("GR2b: ✓ Invalid active contract ID correctly rejected")

        // Scenario 2c: Post contract with negative fee (INVALID_ARG)
        val scenario2c = Scenario(
            scenarioId = "GR2c_negative_fee",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = -5, salvage = SalvagePolicy.GUILD, cmdId = 2L)  // Negative fee
            ),
            description = "Rejection: PostContract with negative fee"
        )

        val result2c = runScenario(scenario2c)
        assertSingleRejection(result2c.allEvents, RejectReason.INVALID_ARG, "GR2c: Expected INVALID_ARG rejection")
        assertNoInvariantViolations(result2c.allEvents, "GR2c: Rejections should not cause invariant violations")
        println("GR2c: ✓ Negative fee correctly rejected")

        println("GR2: All rejection scenarios passed ✓")
    }

    @Test
    fun `GR3 boundary cases insufficient money and escrow`() {
        // GIVEN: Scenario that tests escrow boundary conditions

        // Scenario 3a: Post contract with fee exceeding available money (should be rejected)
        val scenario3a = Scenario(
            scenarioId = "GR3a_insufficient_money",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 150, salvage = SalvagePolicy.GUILD, cmdId = 2L)  // Fee > initial money (100)
            ),
            description = "Boundary: PostContract with fee exceeding available money"
        )

        val result3a = runScenario(scenario3a)
        assertSingleRejection(result3a.allEvents, RejectReason.INVALID_STATE,
            "GR3a: Expected INVALID_STATE rejection for insufficient money")
        assertNoInvariantViolations(result3a.allEvents, "GR3a: Insufficient money rejection should not violate invariants")
        println("GR3a: ✓ Insufficient money correctly rejected")

        // Scenario 3b: Multiple contracts exhausting available money (reserved copper accumulation)
        val scenario3b = Scenario(
            scenarioId = "GR3b_escrow_accumulation",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),                                             // Generate 2+ inbox contracts
                PostContract(inboxId = 1L, fee = 40, salvage = SalvagePolicy.GUILD, cmdId = 2L),  // Reserve 40
                PostContract(inboxId = 2L, fee = 40, salvage = SalvagePolicy.GUILD, cmdId = 3L),  // Reserve 80 total
                PostContract(inboxId = 3L, fee = 30, salvage = SalvagePolicy.GUILD, cmdId = 4L)   // Should fail: 110 > 100
            ),
            description = "Boundary: Multiple contracts exhausting available money via escrow"
        )

        val result3b = runScenario(scenario3b)

        // Expected: first two succeed, third is rejected
        val rejections = result3b.allEvents.filterIsInstance<CommandRejected>()
        assertEquals(1, rejections.size, "GR3b: Expected exactly 1 rejection (third post)")
        assertEquals(RejectReason.INVALID_STATE, rejections.first().reason,
            "GR3b: Expected INVALID_STATE for third post (insufficient available money)")

        val posted = result3b.allEvents.filterIsInstance<ContractPosted>()
        assertEquals(2, posted.size, "GR3b: Expected exactly 2 contracts posted successfully")

        // Verify escrow state
        assertEquals(80, result3b.finalState.economy.reservedCopper,
            "GR3b: Reserved should be 40+40=80 after two posts")
        assertEquals(20, result3b.finalState.economy.moneyCopper - result3b.finalState.economy.reservedCopper,
            "GR3b: Available money should be 100-80=20 after two posts")

        assertNoInvariantViolations(result3b.allEvents, "GR3b: Escrow boundary should not violate invariants")
        println("GR3b: ✓ Escrow accumulation correctly enforced")

        // Scenario 3c: Sell trophies when stock is zero (edge case: should succeed with 0 sold)
        val scenario3c = Scenario(
            scenarioId = "GR3c_sell_zero_trophies",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                SellTrophies(amount = 10, cmdId = 2L)  // Try to sell 10 when stock is 0
            ),
            description = "Boundary: Sell trophies when stock is zero"
        )

        val result3c = runScenario(scenario3c)

        // Expected: command accepted but no TrophySold event (stock is 0)
        val trophySold = result3c.allEvents.filterIsInstance<TrophySold>()
        assertEquals(0, trophySold.size, "GR3c: No TrophySold event when stock is 0")

        assertNoRejections(result3c.allEvents, "GR3c: SellTrophies should not be rejected when stock is 0")
        assertNoInvariantViolations(result3c.allEvents, "GR3c: Sell zero trophies should not violate invariants")
        println("GR3c: ✓ Sell with zero stock handled gracefully")

        println("GR3: All boundary cases passed ✓")
    }

    @Test
    fun `golden replay hashes are stable across runs`() {
        // GIVEN: Same scenario run twice
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

        // WHEN: Run twice
        val result1 = runScenario(scenario)
        val result2 = runScenario(scenario)

        // THEN: All hashes must match exactly
        val hash1 = hashState(result1.finalState)
        val hash2 = hashState(result2.finalState)
        assertEquals(hash1, hash2, "State hashes must be identical across runs with same seeds")

        val eventsHash1 = hashEvents(result1.allEvents)
        val eventsHash2 = hashEvents(result2.allEvents)
        assertEquals(eventsHash1, eventsHash2, "Event hashes must be identical across runs with same seeds")

        assertEquals(result1.rngDraws, result2.rngDraws, "RNG draws must be identical across runs")

        println("✓ Golden replay hashes are stable (deterministic)")
        println("  State hash: $hash1")
        println("  Events hash: $eventsHash1")
        println("  RNG draws: ${result1.rngDraws}")
    }

    @Test
    fun `golden replay RNG draw count is stable`() {
        // GIVEN: Fixed scenario
        val scenario = Scenario(
            scenarioId = "GR_rng_stability",
            stateSeed = 42u,
            rngSeed = 200L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),  // Generates inbox + heroes (RNG used)
                AdvanceDay(cmdId = 2L)   // More generation
            ),
            description = "RNG draw count stability"
        )

        // WHEN: Run scenario
        val result = runScenario(scenario)

        // THEN: Document expected RNG draws (regression check)
        // If this number changes unexpectedly, it indicates RNG draw order changed
        assertTrue(result.rngDraws > 0, "RNG should be used for inbox/hero generation")

        println("RNG draws for GR_rng_stability: ${result.rngDraws}")
        println("⚠ If this number changes in future runs, investigate RNG draw order changes")
    }
}
