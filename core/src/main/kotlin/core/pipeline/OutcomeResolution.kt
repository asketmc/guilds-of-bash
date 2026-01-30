// FILE: core/src/main/kotlin/core/pipeline/OutcomeResolution.kt
package core.pipeline

import core.BalanceSettings
import core.calculateHeroPower
import core.primitives.Outcome
import core.primitives.Quality
import core.rng.Rng
import core.state.Hero

/**
 * Outcome resolution decision model.
 *
 * ## Semantic Ownership
 * Answers exactly one question: **Why SUCCESS / PARTIAL / FAIL / DEATH?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic with explicit rules and formulas.
 *
 * ## Determinism
 * - Uses exactly one RNG roll per decision.
 * - All inputs are explicit; no hidden state.
 * - Draw order preserved for replay compatibility.
 *
 * ## Boundary Rules
 * - Must NOT touch economy.
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object OutcomeResolution {

    /**
     * Decides the outcome for a completed contract.
     *
     * @param hero The hero who completed the contract (null = fallback power).
     * @param contractDifficulty Base difficulty of the contract.
     * @param rng Deterministic RNG for the roll.
     * @return [OutcomeDecision] containing outcome, trophies count, and quality.
     */
    fun decide(hero: Hero?, contractDifficulty: Int, rng: Rng): OutcomeDecision {
        val outcome = resolveOutcome(hero, contractDifficulty, rng)
        val trophiesCount = resolveTrophiesCount(outcome, rng)
        val trophiesQuality = resolveTrophiesQuality(rng)

        return OutcomeDecision(
            outcome = outcome,
            baseTrophiesCount = trophiesCount,
            trophiesQuality = trophiesQuality
        )
    }

    /**
     * Core outcome resolution formula.
     *
     * Uses one RNG roll and fixed probability buckets.
     */
    private fun resolveOutcome(hero: Hero?, contractDifficulty: Int, rng: Rng): Outcome {
        val heroPower = calculateHeroPower(hero)
        val rawSuccessChance = (heroPower - contractDifficulty + BalanceSettings.SUCCESS_FORMULA_OFFSET) *
            BalanceSettings.SUCCESS_FORMULA_MULTIPLIER

        // Clamp success chance to valid range, leaving room for FAIL_CHANCE_MIN
        val maxSuccessForFail = BalanceSettings.PERCENT_ROLL_MAX - BalanceSettings.PARTIAL_CHANCE_FIXED - BalanceSettings.FAIL_CHANCE_MIN
        val pSuccess = rawSuccessChance.coerceIn(BalanceSettings.SUCCESS_CHANCE_MIN, maxSuccessForFail)

        // Fixed partial chance
        val pPartial = BalanceSettings.PARTIAL_CHANCE_FIXED

        // Fail is the remainder, guaranteed >= FAIL_CHANCE_MIN by construction

        val roll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
        return when {
            roll < pSuccess -> Outcome.SUCCESS
            roll < pSuccess + pPartial -> Outcome.PARTIAL
            else -> {
                // DEATH tail: fixed high-roll death chance without extra RNG draws
                if (roll >= (BalanceSettings.PERCENT_ROLL_MAX - 5)) {
                    // PoC/MVP: sometimes emit MISSING as a narrative alias for DEATH while keeping backend effects identical.
                    val deathOutcome = Outcome.DEATH
                    // Use same RNG stream to preserve draw order determinism — use another nextInt on the same RNG.
                    val subRoll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
                    if (subRoll < BalanceSettings.MISSING_CHANCE_PERCENT) Outcome.MISSING else deathOutcome
                } else {
                    Outcome.FAIL
                }
            }
        }
    }

    /**
     * Determines trophy count based on outcome.
     */
    private fun resolveTrophiesCount(outcome: Outcome, rng: Rng): Int {
        return when (outcome) {
            Outcome.SUCCESS -> 1 + rng.nextInt(3)
            Outcome.PARTIAL -> 1
            else -> 0
        }
    }

    /**
     * Determines trophy quality.
     */
    private fun resolveTrophiesQuality(rng: Rng): Quality {
        return Quality.entries[rng.nextInt(Quality.entries.size)]
    }
}

/**
 * Decision output from outcome resolution.
 *
 * This DTO is RNG-free and can be tested in isolation.
 */
data class OutcomeDecision(
    /** The resolved outcome (SUCCESS, PARTIAL, FAIL, DEATH). */
    val outcome: Outcome,
    /** Base trophies count before theft processing. */
    val baseTrophiesCount: Int,
    /** Quality of the trophies. */
    val trophiesQuality: Quality
) {
    /** Whether this outcome requires manual player close (PARTIAL only). */
    val requiresPlayerClose: Boolean get() = outcome == Outcome.PARTIAL
}
