package core.pipeline

import core.primitives.ActiveStatus
import core.state.ActiveContract

/**
 * WIP progression model.
 *
 * ## Semantic Ownership
 * Answers: **Why daysRemaining changed to X? Why WIP became RETURN_READY?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic with explicit rules.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT resolve outcomes.
 * - Must NOT emit events.
 * - Must NOT mutate state directly.
 */
object WipProgression {

    /**
     * Computes WIP advancement for all active contracts.
     *
     * @param actives Current active contracts.
     * @return [WipProgressionResult] with updated actives and completion list.
     */
    fun advance(actives: List<ActiveContract>): WipProgressionResult {
        val sortedActives = actives.sortedBy { it.id.value }
        val updated = ArrayList<ActiveContract>(actives.size)
        val completedIds = ArrayList<Int>()
        val advances = ArrayList<WipAdvance>()

        for (active in sortedActives) {
            if (active.status != ActiveStatus.WIP) {
                updated.add(active)
                continue
            }

            val newDaysRemaining = active.daysRemaining - 1

            advances.add(
                WipAdvance(
                    activeContractId = active.id.value,
                    oldDaysRemaining = active.daysRemaining,
                    newDaysRemaining = newDaysRemaining
                )
            )

            if (newDaysRemaining == 0) {
                completedIds.add(active.id.value)
                // When days hit 0, the contract is no longer WIP.
                // Mark as RETURN_READY so invariants don't observe (WIP, 0 days).
                updated.add(active.copy(daysRemaining = 0, status = ActiveStatus.RETURN_READY))
            } else {
                updated.add(active.copy(daysRemaining = newDaysRemaining))
            }
        }

        return WipProgressionResult(
            updatedActives = updated,
            completedActiveIds = completedIds,
            advances = advances
        )
    }
}

/**
 * Single WIP advance record.
 */
data class WipAdvance(
    val activeContractId: Int,
    val oldDaysRemaining: Int,
    val newDaysRemaining: Int
)

/**
 * Result of WIP progression.
 */
data class WipProgressionResult(
    /** Updated active contracts with new daysRemaining. */
    val updatedActives: List<ActiveContract>,
    /** IDs of contracts that reached 0 days and need resolution. */
    val completedActiveIds: List<Int>,
    /** All advancement records for events. */
    val advances: List<WipAdvance>
)
