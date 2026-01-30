// FILE: core/src/main/kotlin/core/pipeline/BoardStatusModel.kt
package core.pipeline

import core.primitives.ActiveStatus
import core.primitives.BoardStatus
import core.primitives.ContractId
import core.state.ActiveContract
import core.state.BoardContract

/**
 * Board status decision model.
 *
 * ## Semantic Ownership
 * Answers: **Why board status changed to COMPLETED?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic with explicit rules.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state directly.
 */
object BoardStatusModel {

    /**
     * Determines if a board contract should be marked COMPLETED.
     *
     * A board becomes COMPLETED when all associated active contracts are CLOSED.
     *
     * @param boardContract The board contract to evaluate.
     * @param activeContracts Current active contracts (after updates).
     * @return [BoardStatusDecision] with result.
     */
    fun shouldComplete(
        boardContract: BoardContract?,
        activeContracts: List<ActiveContract>
    ): BoardStatusDecision {
        if (boardContract == null || boardContract.status != BoardStatus.LOCKED) {
            return BoardStatusDecision(shouldComplete = false)
        }

        val hasNonClosedActives = activeContracts.any { active ->
            active.boardContractId == boardContract.id && active.status != ActiveStatus.CLOSED
        }

        return BoardStatusDecision(shouldComplete = !hasNonClosedActives)
    }

    /**
     * Updates board list, marking the specified board as COMPLETED if applicable.
     *
     * @param boards Current board contracts.
     * @param boardIdToComplete ID of board to potentially complete.
     * @param activeContracts Current active contracts (for completion check).
     * @return Updated board list.
     */
    fun updateBoardStatus(
        boards: List<BoardContract>,
        boardIdToComplete: ContractId?,
        activeContracts: List<ActiveContract>
    ): List<BoardContract> {
        if (boardIdToComplete == null) return boards

        val boardContract = boards.firstOrNull { it.id == boardIdToComplete }
        val decision = shouldComplete(boardContract, activeContracts)

        if (!decision.shouldComplete) return boards

        return boards.map { b ->
            if (b.id == boardIdToComplete) b.copy(status = BoardStatus.COMPLETED) else b
        }
    }
}

/**
 * Result of board status check.
 */
data class BoardStatusDecision(
    /** Whether the board should be marked COMPLETED. */
    val shouldComplete: Boolean
)
