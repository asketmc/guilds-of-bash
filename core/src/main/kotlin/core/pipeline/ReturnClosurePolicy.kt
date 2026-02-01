// FILE: core/src/main/kotlin/core/pipeline/ReturnClosurePolicy.kt
package core.pipeline

import core.primitives.ProofPolicy
import core.primitives.Quality

/**
 * Return closure policy model.
 *
 * ## Semantic Ownership
 * Answers: **Why can/cannot close this return?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic with explicit rules.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT modify state.
 * - Must NOT emit events.
 */
object ReturnClosurePolicy {

    /**
     * Checks if a return can be manually closed under current proof policy.
     *
     * @param proofPolicy Current guild proof policy.
     * @param trophiesQuality Quality of returned trophies.
     * @param suspectedTheft Whether theft was suspected.
     * @return [ClosureCheck] with decision and reason.
     */
    fun canClose(
        proofPolicy: ProofPolicy,
        trophiesQuality: Quality,
        suspectedTheft: Boolean
    ): ClosureCheck {
        return when (proofPolicy) {
            ProofPolicy.STRICT -> {
                when {
                    trophiesQuality == Quality.DAMAGED -> ClosureCheck(
                        allowed = false,
                        reason = "strict_policy_damaged_proof"
                    )
                    suspectedTheft -> ClosureCheck(
                        allowed = false,
                        reason = "strict_policy_theft_suspected"
                    )
                    else -> ClosureCheck(allowed = true, reason = null)
                }
            }
            ProofPolicy.SOFT -> ClosureCheck(allowed = true, reason = null)
            ProofPolicy.FAST -> ClosureCheck(allowed = true, reason = null)
        }
    }
}

/**
 * Result of closure policy check.
 */
data class ClosureCheck(
    /** Whether closure is allowed. */
    val allowed: Boolean,
    /** Reason for denial if not allowed (null if allowed). */
    val reason: String?
)
