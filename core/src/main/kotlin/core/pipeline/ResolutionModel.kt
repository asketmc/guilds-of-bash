// FILE: core/src/main/kotlin/core/pipeline/ResolutionModel.kt
package core.pipeline

import core.primitives.Outcome
import core.primitives.Quality
import core.rng.Rng
import core.state.BoardContract
import core.state.Hero

/**
 * Contract resolution decision model.
 *
 * ## Semantic Ownership
 * Answers: **What is the complete resolution for a completed contract?**
 *
 * Combines outcome, theft, and trophies into a single decision.
 *
 * ## Stability
 * - STABLE: Pure decision logic that delegates to specialized models.
 *
 * ## Determinism
 * - RNG draw order: outcome (1-3 draws), then theft (1 draw).
 * - All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state directly.
 */
object ResolutionModel {

    /**
     * Computes the complete resolution for a completed contract.
     *
     * @param hero The hero who completed the contract (null = fallback).
     * @param boardContract The board contract being resolved (nullable).
     * @param contractDifficulty Base difficulty of the contract.
     * @param rng Deterministic RNG.
     * @return [ContractResolutionDecision] with all resolution details.
     */
    fun computeResolution(
        hero: Hero?,
        boardContract: BoardContract?,
        contractDifficulty: Int,
        rng: Rng
    ): ContractResolutionDecision {
        // Step 1: Outcome resolution
        val outcomeDecision = OutcomeResolution.decide(hero, contractDifficulty, rng)
        val effectiveOutcome = outcomeDecision.outcome

        // Step 2: Theft resolution (DEATH overrides theft)
        val baseTheftDecision = TheftModel.decide(
            hero,
            boardContract,
            outcomeDecision.baseTrophiesCount,
            rng
        )
        val theftDecision = if (effectiveOutcome == Outcome.DEATH || effectiveOutcome == Outcome.MISSING) {
            baseTheftDecision.copy(
                theftOccurred = false,
                expectedTrophiesCount = 0,
                reportedTrophiesCount = 0
            )
        } else baseTheftDecision

        return ContractResolutionDecision(
            outcome = effectiveOutcome,
            trophiesCount = theftDecision.reportedTrophiesCount,
            trophiesQuality = outcomeDecision.trophiesQuality,
            requiresPlayerClose = outcomeDecision.requiresPlayerClose,
            theftOccurred = theftDecision.theftOccurred,
            expectedTrophiesCount = theftDecision.expectedTrophiesCount
        )
    }

    /**
     * Determines if this resolution counts toward stability tracking.
     *
     * @param outcome The resolved outcome.
     * @param requiresPlayerClose Whether manual close is required.
     * @return [StabilityContribution] with success/fail counts.
     */
    fun computeStabilityContribution(
        outcome: Outcome,
        requiresPlayerClose: Boolean
    ): StabilityContribution {
        if (requiresPlayerClose) {
            return StabilityContribution(successCount = 0, failCount = 0)
        }
        return when (outcome) {
            Outcome.SUCCESS -> StabilityContribution(successCount = 1, failCount = 0)
            Outcome.FAIL, Outcome.DEATH, Outcome.MISSING -> StabilityContribution(successCount = 0, failCount = 1)
            Outcome.PARTIAL -> StabilityContribution(successCount = 0, failCount = 0)
        }
    }
}

/**
 * Complete resolution decision for a contract.
 */
data class ContractResolutionDecision(
    /** Resolved outcome. */
    val outcome: Outcome,
    /** Final trophy count (after theft). */
    val trophiesCount: Int,
    /** Trophy quality. */
    val trophiesQuality: Quality,
    /** Whether player must manually close this return. */
    val requiresPlayerClose: Boolean,
    /** Whether theft was detected. */
    val theftOccurred: Boolean,
    /** Expected trophies (before theft). */
    val expectedTrophiesCount: Int
)

/**
 * Contribution to stability tracking.
 */
data class StabilityContribution(
    val successCount: Int,
    val failCount: Int
)
