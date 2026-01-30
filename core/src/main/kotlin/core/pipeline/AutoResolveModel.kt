// FILE: core/src/main/kotlin/core/pipeline/AutoResolveModel.kt
package core.pipeline

import core.BalanceSettings
import core.primitives.AutoResolveBucket
import core.primitives.ContractId
import core.rng.Rng
import core.state.ContractDraft

/**
 * Auto-resolve decision model for inbox drafts.
 *
 * ## Semantic Ownership
 * Answers: **What happens to stale inbox drafts?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic with explicit rules.
 *
 * ## Determinism
 * - Uses RNG in deterministic order (one roll per draft).
 * - All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state directly.
 */
object AutoResolveModel {

    /**
     * Computes auto-resolve outcomes for due inbox drafts.
     *
     * @param inbox Current inbox drafts.
     * @param currentDay Current day index.
     * @param rng Deterministic RNG.
     * @return [AutoResolveResult] with decisions and updated inbox.
     */
    fun computeAutoResolve(
        inbox: List<ContractDraft>,
        currentDay: Int,
        rng: Rng
    ): AutoResolveResult {
        val dueDrafts = inbox.filter { it.nextAutoResolveDay <= currentDay }

        if (dueDrafts.isEmpty()) {
            return AutoResolveResult(
                decisions = emptyList(),
                updatedInbox = inbox,
                cumulativeStabilityDelta = 0
            )
        }

        val updatedInbox = inbox.toMutableList()
        val decisions = ArrayList<AutoResolveDecision>(dueDrafts.size)
        var cumulativeStabilityDelta = 0

        for (draft in dueDrafts) {
            val bucket = when (rng.nextInt(3)) {
                0 -> AutoResolveBucket.GOOD
                1 -> AutoResolveBucket.NEUTRAL
                else -> AutoResolveBucket.BAD
            }

            decisions.add(
                AutoResolveDecision(
                    draftId = draft.id,
                    bucket = bucket
                )
            )

            when (bucket) {
                AutoResolveBucket.GOOD -> {
                    updatedInbox.removeIf { it.id == draft.id }
                }
                AutoResolveBucket.NEUTRAL -> {
                    val idx = updatedInbox.indexOfFirst { it.id == draft.id }
                    if (idx >= 0) {
                        updatedInbox[idx] = draft.copy(
                            nextAutoResolveDay = currentDay + BalanceSettings.AUTO_RESOLVE_INTERVAL_DAYS
                        )
                    }
                }
                AutoResolveBucket.BAD -> {
                    updatedInbox.removeIf { it.id == draft.id }
                    cumulativeStabilityDelta -= BalanceSettings.STABILITY_PENALTY_BAD_AUTO_RESOLVE
                }
            }
        }

        return AutoResolveResult(
            decisions = decisions,
            updatedInbox = updatedInbox.toList(),
            cumulativeStabilityDelta = cumulativeStabilityDelta
        )
    }
}

/**
 * Single auto-resolve decision for one draft.
 */
data class AutoResolveDecision(
    /** ID of the draft being resolved. */
    val draftId: ContractId,
    /** Outcome bucket. */
    val bucket: AutoResolveBucket
)

/**
 * Result of auto-resolve computation.
 */
data class AutoResolveResult(
    /** Decisions for each due draft. */
    val decisions: List<AutoResolveDecision>,
    /** Updated inbox after applying decisions. */
    val updatedInbox: List<ContractDraft>,
    /** Cumulative stability change from BAD outcomes. */
    val cumulativeStabilityDelta: Int
)
