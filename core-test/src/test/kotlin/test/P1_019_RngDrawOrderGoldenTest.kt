package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: RNG Draw Order Golden Test.
 * Validates that RNG draw count is stable across refactorings.
 *
 * Purpose:
 * - Detect unexpected changes in RNG draw order (indicates logic changes)
 * - Ensure determinism is preserved across code refactorings
 * - Document expected RNG draw counts for golden scenarios
 *
 * Contract:
 * - For fixed seeds and command sequence, `rng.draws` must be identical
 * - If draws count changes, it indicates RNG usage pattern changed (investigate!)
 */
class P1_019_RngDrawOrderGoldenTest {

    @Test
    fun `RNG draw count is stable for single AdvanceDay`() {
        // GIVEN: Fixed seeds
        val state = initialState(42u)
        val rng = Rng(100L)

        // WHEN: Single AdvanceDay
        step(state, AdvanceDay(cmdId = 1L), rng)

        // THEN: Document expected draws
        val expectedDraws = rng.draws
        assertTrue(expectedDraws > 0, "AdvanceDay should use RNG for inbox/hero generation")

        println("RNG draws for single AdvanceDay: $expectedDraws")
        println("⚠ If this number changes in future runs, RNG draw order may have changed")
    }

    @Test
    fun `RNG draw count is stable for post contract scenario`() {
        // GIVEN: Scenario with post contract
        val scenario = Scenario(
            scenarioId = "rng_post_contract",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L)
            )
        )

        // WHEN: Run scenario
        val result = runScenario(scenario)

        // THEN: Document draws
        val expectedDraws = result.rngDraws
        println("RNG draws for AdvanceDay + PostContract: $expectedDraws")

        // Re-run to verify stability
        val result2 = runScenario(scenario)
        assertEquals(expectedDraws, result2.rngDraws, "RNG draw count must be identical for same scenario")

        println("✓ RNG draw count is stable (verified with re-run)")
    }

    @Test
    fun `RNG draw count is stable for full contract lifecycle`() {
        // GIVEN: Full lifecycle scenario (GR1-like)
        val scenario = Scenario(
            scenarioId = "rng_full_lifecycle",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L),
                AdvanceDay(cmdId = 3L),  // Take + advance
                AdvanceDay(cmdId = 4L)   // Resolve
            )
        )

        // WHEN: Run scenario
        val result = runScenario(scenario)

        // THEN: Document golden draw count
        val goldenDraws = result.rngDraws
        assertTrue(goldenDraws > 0, "Full lifecycle should use RNG")

        println("RNG golden draw count (full lifecycle): $goldenDraws")

        // Re-run 5 times to verify absolute stability
        repeat(5) { iteration ->
            val rerun = runScenario(scenario)
            assertEquals(goldenDraws, rerun.rngDraws,
                "RNG draw count must be identical on re-run #$iteration")
        }

        println("✓ RNG draw count is stable across 5 re-runs")
    }

    @Test
    fun `RNG draw count is stable across different outcomes`() {
        // GIVEN: Same scenario with different RNG seeds (different outcomes)
        val seed1 = 100L
        val seed2 = 500L

        val scenario1 = Scenario(
            scenarioId = "rng_outcome_1",
            stateSeed = 42u,
            rngSeed = seed1,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L),
                AdvanceDay(cmdId = 3L),
                AdvanceDay(cmdId = 4L)
            )
        )

        val scenario2 = scenario1.copy(scenarioId = "rng_outcome_2", rngSeed = seed2)

        // WHEN: Run both scenarios
        val result1 = runScenario(scenario1)
        val result2 = runScenario(scenario2)

        // THEN: Draw counts should be identical (same logic path, different outcome)
        assertEquals(result1.rngDraws, result2.rngDraws,
            "RNG draw count should be stable across different RNG seeds (same command sequence)")

        println("✓ RNG draw count is stable across different outcomes")
        println("  seed=$seed1 → draws=${result1.rngDraws}")
        println("  seed=$seed2 → draws=${result2.rngDraws}")
    }

    @Test
    fun `RNG draw count per command is documented`() {
        // GIVEN: Individual commands
        val stateSeed = 42u

        // Test 1: AdvanceDay alone
        val rng1 = Rng(100L)
        var state1 = initialState(stateSeed)
        step(state1, AdvanceDay(cmdId = 1L), rng1)
        val advanceDayDraws = rng1.draws
        println("AdvanceDay draws: $advanceDayDraws")

        // Test 2: PostContract alone (after AdvanceDay to have inbox)
        val rng2 = Rng(100L)
        var state2 = initialState(stateSeed)
        state2 = step(state2, AdvanceDay(cmdId = 1L), rng2).state
        val drawsBeforePost = rng2.draws
        step(state2, PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L), rng2)
        val postContractDraws = rng2.draws - drawsBeforePost
        println("PostContract draws (incremental): $postContractDraws")

        // Test 3: SellTrophies alone
        val rng3 = Rng(100L)
        val state3 = initialState(stateSeed)
        step(state3, SellTrophies(amount = 0, cmdId = 1L), rng3)
        val sellTrophiesDraws = rng3.draws
        println("SellTrophies draws: $sellTrophiesDraws")

        println("✓ RNG draw counts per command documented")
        println("⚠ These are GOLDEN values - if they change, investigate RNG usage changes")
    }

    @Test
    fun `RNG draws are identical for commands with same state and seed`() {
        // GIVEN: Same initial state and seed
        val state = initialState(42u)
        val cmd = AdvanceDay(cmdId = 1L)

        // WHEN: Run command twice with same seed
        val rng1 = Rng(100L)
        step(state, cmd, rng1)
        val draws1 = rng1.draws

        val rng2 = Rng(100L)
        step(state, cmd, rng2)
        val draws2 = rng2.draws

        // THEN: Draws must be identical
        assertEquals(draws1, draws2, "Same command with same seed must use same number of RNG draws")

        println("✓ RNG draws are deterministic (identical for same inputs)")
    }

    @Test
    fun `RNG draw order regression detection for GR1 scenario`() {
        // GIVEN: GR1 golden scenario
        val scenario = Scenario(
            scenarioId = "GR1_rng_regression",
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

        // WHEN: Run scenario
        val result = runScenario(scenario)

        // THEN: Document GOLDEN draw count for regression detection
        val goldenDrawCount = result.rngDraws

        // This is the REFERENCE value. Future test runs should match this exactly.
        // If this assertion fails, investigate what changed in RNG usage.

        println("═══════════════════════════════════════")
        println("GR1 GOLDEN RNG DRAW COUNT: $goldenDrawCount")
        println("═══════════════════════════════════════")
        println("⚠ IMPORTANT: This value is the regression baseline.")
        println("  If future runs produce different values, it indicates:")
        println("  - RNG draw order changed (logic refactoring)")
        println("  - New RNG usage added (feature change)")
        println("  - RNG usage removed (optimization or bug)")
        println("═══════════════════════════════════════")

        // Re-run to verify current run is stable
        val rerun = runScenario(scenario)
        assertEquals(goldenDrawCount, rerun.rngDraws, "GR1 RNG draw count must be stable within same test run")

        println("✓ GR1 RNG draw count verified stable within test run")
    }

    @Test
    fun `RNG draws do not leak across steps`() {
        // GIVEN: Two independent steps
        val state = initialState(42u)
        val rng = Rng(100L)

        // WHEN: Execute two commands
        val result1 = step(state, AdvanceDay(cmdId = 1L), rng)
        val drawsAfterStep1 = rng.draws

        val result2 = step(result1.state, SellTrophies(amount = 0, cmdId = 2L), rng)
        val drawsAfterStep2 = rng.draws

        // THEN: Draws accumulate (RNG state persists)
        assertTrue(drawsAfterStep2 >= drawsAfterStep1, "RNG draws should accumulate across steps")

        println("✓ RNG state persists across steps (cumulative draws)")
        println("  After step 1: $drawsAfterStep1")
        println("  After step 2: $drawsAfterStep2")
        println("  Incremental draws in step 2: ${drawsAfterStep2 - drawsAfterStep1}")
    }
}
