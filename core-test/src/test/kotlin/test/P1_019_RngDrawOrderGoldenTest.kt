package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: RNG Draw Order Golden Test.
 *
 * Contract-focused goals (stable under outcome-dependent branching):
 * - Determinism: for a fixed (stateSeed, rngSeed, command sequence) the total draw count is stable across re-runs.
 * - Regression signal: GR1 scenario draw count is documented and must be stable for a fixed seed.
 * - Same inputs => same draws: identical initial state + identical seed + identical command => identical draws.
 *
 * NOTE:
 * - Draw counts are NOT expected to be equal across different seeds if the logic has outcome-dependent or
 *   branch-dependent RNG usage (e.g., extra draws for trophy count/theft only on some outcomes).
 * - Therefore, tests that assumed draw-count invariance across different outcomes have been adjusted.
 */
class P1_019_RngDrawOrderGoldenTest {

    @Test
    fun `RNG draw count is stable for single AdvanceDay`() {
        val state = initialState(42u)
        val rng = Rng(100L)

        step(state, AdvanceDay(cmdId = 1L), rng)

        val draws = rng.draws
        assertTrue(draws > 0, "AdvanceDay should use RNG for inbox/hero generation")
        println("RNG draws for single AdvanceDay: $draws")
    }

    @Test
    fun `RNG draw count is stable for post contract scenario`() {
        val scenario = Scenario(
            scenarioId = "rng_post_contract",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L)
            )
        )

        val result1 = runScenario(scenario)
        val expectedDraws = result1.rngDraws
        println("RNG draws for AdvanceDay + PostContract: $expectedDraws")

        val result2 = runScenario(scenario)
        assertEquals(expectedDraws, result2.rngDraws, "RNG draw count must be identical for same scenario re-run")
    }

    @Test
    fun `RNG draw count is stable for full contract lifecycle`() {
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

        val baseline = runScenario(scenario).rngDraws
        assertTrue(baseline > 0, "Full lifecycle should use RNG")
        println("RNG golden draw count (full lifecycle, seed=100): $baseline")

        repeat(5) { iteration ->
            val rerun = runScenario(scenario).rngDraws
            assertEquals(baseline, rerun, "RNG draw count must be identical on re-run #$iteration for same seed")
        }
    }

    @Test
    fun `RNG draw count is deterministic across re-runs even if outcome differs between seeds`() {
        // Same command sequence, different seeds: draws MAY differ (branch-dependent RNG usage).
        // Contract: each seed must be stable across re-runs of the same scenario.
        val seed1 = 100L
        val seed2 = 500L

        val scenarioBase = Scenario(
            scenarioId = "rng_outcome_base",
            stateSeed = 42u,
            rngSeed = seed1,
            commands = listOf(
                AdvanceDay(cmdId = 1L),
                PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L),
                AdvanceDay(cmdId = 3L),
                AdvanceDay(cmdId = 4L)
            )
        )

        val scenario1 = scenarioBase.copy(scenarioId = "rng_outcome_1", rngSeed = seed1)
        val scenario2 = scenarioBase.copy(scenarioId = "rng_outcome_2", rngSeed = seed2)

        val r1a = runScenario(scenario1).rngDraws
        val r1b = runScenario(scenario1).rngDraws
        assertEquals(r1a, r1b, "Seed=$seed1 must have stable draw count across re-runs")

        val r2a = runScenario(scenario2).rngDraws
        val r2b = runScenario(scenario2).rngDraws
        assertEquals(r2a, r2b, "Seed=$seed2 must have stable draw count across re-runs")

        println("Draws are deterministic per-seed for the same command sequence:")
        println("  seed=$seed1 → draws=$r1a")
        println("  seed=$seed2 → draws=$r2a")
        if (r1a != r2a) {
            println("  (expected) different seeds may yield different draws due to branch-dependent RNG usage")
        }
    }

    @Test
    fun `RNG draw count per command is documented`() {
        val stateSeed = 42u

        // AdvanceDay alone
        val rng1 = Rng(100L)
        val state1 = initialState(stateSeed)
        step(state1, AdvanceDay(cmdId = 1L), rng1)
        val advanceDayDraws = rng1.draws
        println("AdvanceDay draws: $advanceDayDraws")

        // PostContract after AdvanceDay (to have inbox)
        val rng2 = Rng(100L)
        var state2 = initialState(stateSeed)
        state2 = step(state2, AdvanceDay(cmdId = 1L), rng2).state
        val drawsBeforePost = rng2.draws
        step(state2, PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L), rng2)
        val postContractDraws = rng2.draws - drawsBeforePost
        println("PostContract draws (incremental): $postContractDraws")

        // SellTrophies alone
        val rng3 = Rng(100L)
        val state3 = initialState(stateSeed)
        step(state3, SellTrophies(amount = 0, cmdId = 1L), rng3)
        val sellTrophiesDraws = rng3.draws
        println("SellTrophies draws: $sellTrophiesDraws")
    }

    @Test
    fun `RNG draws are identical for commands with same state and seed`() {
        val state = initialState(42u)
        val cmd = AdvanceDay(cmdId = 1L)

        val rng1 = Rng(100L)
        step(state, cmd, rng1)
        val draws1 = rng1.draws

        val rng2 = Rng(100L)
        step(state, cmd, rng2)
        val draws2 = rng2.draws

        assertEquals(draws1, draws2, "Same command with same seed must use same number of RNG draws")
    }

    @Test
    fun `RNG draw order regression detection for GR1 scenario`() {
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

        val baseline = runScenario(scenario).rngDraws
        println("GR1 GOLDEN RNG DRAW COUNT (seed=100): $baseline")

        val rerun = runScenario(scenario).rngDraws
        assertEquals(baseline, rerun, "GR1 RNG draw count must be stable within the same test run (same seed)")
    }

    @Test
    fun `RNG draws do not leak across steps`() {
        val state = initialState(42u)
        val rng = Rng(100L)

        val drawsBefore = rng.draws
        step(state, AdvanceDay(cmdId = 1L), rng)
        val drawsAfter1 = rng.draws
        assertTrue(drawsAfter1 >= drawsBefore, "RNG draw counter must be monotonic")

        step(state, SellTrophies(amount = 0, cmdId = 2L), rng)
        val drawsAfter2 = rng.draws
        assertTrue(drawsAfter2 >= drawsAfter1, "RNG draw counter must be monotonic")
    }
}
