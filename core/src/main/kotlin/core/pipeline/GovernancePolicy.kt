// FILE: core/src/main/kotlin/core/pipeline/GovernancePolicy.kt
package core.pipeline

import core.primitives.ProofPolicy

/**
 * Governance policy model.
 *
 * ## Semantic Ownership
 * Answers: **What governance rules apply?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic.
 *
 * ## Boundary Rules
 * - Must NOT modify state.
 * - Must NOT emit events.
 */
object GovernancePolicy {

    /**
     * Computes the new proof policy.
     *
     * @param oldPolicy Current policy.
     * @param newPolicy Requested policy.
     * @return [ProofPolicyChange] with old and new values.
     */
    fun computePolicyChange(oldPolicy: ProofPolicy, newPolicy: ProofPolicy): ProofPolicyChange {
        return ProofPolicyChange(
            oldPolicy = oldPolicy,
            newPolicy = newPolicy,
            changed = oldPolicy != newPolicy
        )
    }
}

/**
 * Result of proof policy change computation.
 */
data class ProofPolicyChange(
    val oldPolicy: ProofPolicy,
    val newPolicy: ProofPolicy,
    val changed: Boolean
)
