// FILE: core/src/main/kotlin/core/pipeline/StabilityModel.kt
package core.pipeline

import core.BalanceSettings

/**
 * Stability decision model.
 *
 * ## Semantic Ownership
 * Answers: **Why stability changed by X?**
 *
 * ## Stability Gradient
 * STABLE: Pure calculation logic with explicit rules.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object StabilityModel {

    /**
     * Computes stability delta from contract results.
     *
     * @param successfulReturns Number of successful auto-closes this day.
     * @param failedReturns Number of failed auto-closes this day.
     * @param oldStability Current stability value.
     * @return [StabilityUpdate] with computed changes.
     */
    fun computeFromResults(
        successfulReturns: Int,
        failedReturns: Int,
        oldStability: Int
    ): StabilityUpdate {
        val delta = successfulReturns - failedReturns
        val newStability = (oldStability + delta).coerceIn(0, 100)
        val changed = newStability != oldStability

        return StabilityUpdate(
            oldStability = oldStability,
            newStability = newStability,
            delta = delta,
            changed = changed
        )
    }

    /**
     * Computes stability penalty from bad auto-resolve.
     *
     * @param badResolveCount Number of BAD bucket outcomes.
     * @param oldStability Current stability value.
     * @return [StabilityUpdate] with computed penalty.
     */
    fun computeAutoResolvePenalty(
        badResolveCount: Int,
        oldStability: Int
    ): StabilityUpdate {
        val penalty = badResolveCount * BalanceSettings.STABILITY_PENALTY_BAD_AUTO_RESOLVE
        val newStability = (oldStability - penalty).coerceIn(0, 100)
        val changed = newStability != oldStability

        return StabilityUpdate(
            oldStability = oldStability,
            newStability = newStability,
            delta = -penalty,
            changed = changed
        )
    }
}

/**
 * Result of stability computation.
 */
data class StabilityUpdate(
    val oldStability: Int,
    val newStability: Int,
    val delta: Int,
    val changed: Boolean
)
