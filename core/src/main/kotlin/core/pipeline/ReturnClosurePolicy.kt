// FILE: core/src/main/kotlin/core/pipeline/ReturnClosurePolicy.kt
package core.pipeline

import core.ReturnDecision
import core.primitives.ProofPolicy
import core.primitives.Quality

/**
 * Return closure policy model.
 *
 * ## Semantic Ownership
 * Answers: **Can this return be closed with the given decision?**
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
     * Checks if a return can be manually closed with the given decision.
     *
     * ## MVP Changes (accept|reject):
     * - STRICT policy now requires an explicit decision (ACCEPT or REJECT).
     * - REJECT always allowed (terminates lifecycle without payment).
     * - ACCEPT may be denied under STRICT if proof is damaged or theft suspected.
     *
     * @param proofPolicy Current guild proof policy.
     * @param decision Player's decision (ACCEPT or REJECT), or null for legacy default.
     * @param trophiesQuality Quality of returned trophies.
     * @param suspectedTheft Whether theft was suspected.
     * @return [ClosureCheck] with decision and reason.
     */
    fun canClose(
        proofPolicy: ProofPolicy,
        decision: ReturnDecision?,
        trophiesQuality: Quality,
        suspectedTheft: Boolean
    ): ClosureCheck {
        // Under STRICT, decision must be explicit
        if (proofPolicy == ProofPolicy.STRICT && decision == null) {
            return ClosureCheck(
                allowed = false,
                reason = "strict_policy_requires_decision"
            )
        }

        // REJECT is always allowed (unblocks gameplay)
        if (decision == ReturnDecision.REJECT) {
            return ClosureCheck(allowed = true, reason = null)
        }

        // ACCEPT under STRICT: check proof quality and theft suspicion
        if (proofPolicy == ProofPolicy.STRICT) {
            when {
                trophiesQuality == Quality.DAMAGED -> return ClosureCheck(
                    allowed = false,
                    reason = "strict_policy_damaged_proof"
                )
                suspectedTheft -> return ClosureCheck(
                    allowed = false,
                    reason = "strict_policy_theft_suspected"
                )
            }
        }

        // All other cases: allowed
        return ClosureCheck(allowed = true, reason = null)
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
