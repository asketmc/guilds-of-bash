// FILE: core/src/main/kotlin/core/pipeline/TheftModel.kt
package core.pipeline

import core.BalanceSettings
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.BoardContract
import core.state.Hero

/**
 * Theft decision model.
 *
 * ## Semantic Ownership
 * Answers exactly one question: **Why theft suspected?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic with explicit rules.
 *
 * ## Determinism
 * - Uses exactly one RNG roll per decision.
 * - All inputs are explicit; no hidden state.
 *
 * ## Boundary Rules
 * - Must NOT change trophies stock.
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object TheftModel {

    /**
     * Decides whether theft occurred and computes reported trophies.
     *
     * @param hero The hero under suspicion.
     * @param board The contract's board terms.
     * @param baseTrophiesCount Trophies before potential theft.
     * @param rng Deterministic RNG for the roll.
     * @return [TheftDecision] containing theft flag and reported trophies.
     */
    fun decide(
        hero: Hero?,
        board: BoardContract?,
        baseTrophiesCount: Int,
        rng: Rng
    ): TheftDecision {
        if (hero == null || board == null || baseTrophiesCount <= 0) {
            return TheftDecision(
                theftOccurred = false,
                reportedTrophiesCount = baseTrophiesCount,
                expectedTrophiesCount = baseTrophiesCount
            )
        }

        val theftChance = computeTheftChance(hero, board)
        val theftRoll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)

        return if (theftRoll < theftChance) {
            val stolenAmount = (baseTrophiesCount + 1) / 2
            val reportedAmount = (baseTrophiesCount - stolenAmount).coerceAtLeast(0)
            TheftDecision(
                theftOccurred = true,
                reportedTrophiesCount = reportedAmount,
                expectedTrophiesCount = baseTrophiesCount
            )
        } else {
            TheftDecision(
                theftOccurred = false,
                reportedTrophiesCount = baseTrophiesCount,
                expectedTrophiesCount = baseTrophiesCount
            )
        }
    }

    /**
     * Theft probability policy.
     *
     * Produces a single integer percentage in a fixed range.
     * All inputs are explicit. No hidden state is consulted.
     */
    private fun computeTheftChance(hero: Hero, board: BoardContract): Int {
        return when {
            board.salvage == SalvagePolicy.GUILD && board.fee == 0 ->
                hero.traits.greed
            board.salvage == SalvagePolicy.GUILD && board.fee > 0 ->
                (hero.traits.greed - board.fee / 2).coerceAtLeast(0)
            board.salvage == SalvagePolicy.HERO -> 0
            board.salvage == SalvagePolicy.SPLIT ->
                ((hero.traits.greed - hero.traits.honesty) / 2).coerceAtLeast(0)
            else -> 0
        }
    }
}

/**
 * Decision output from theft resolution.
 *
 * This DTO is RNG-free and can be tested in isolation.
 */
data class TheftDecision(
    /** Whether theft was detected. */
    val theftOccurred: Boolean,
    /** Trophies count after potential theft. */
    val reportedTrophiesCount: Int,
    /** Expected trophies count before theft. */
    val expectedTrophiesCount: Int
)
