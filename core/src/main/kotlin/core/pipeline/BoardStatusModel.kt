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

        val hasNonClosedActives = activeContracts.any {
            it.boardContractId == boardContract.id && it.status != ActiveStatus.CLOSED
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

        return boards.map {
            if (it.id == boardIdToComplete) it.copy(status = BoardStatus.COMPLETED) else it
        }
    }

    /**
     * Terminal completion helper: complete a single board contract and extract an archived snapshot.
     *
     * ## When to use this
     * Use this function only when the board contract is **done forever** from a gameplay perspective:
     * - auto-close on SUCCESS / FAIL / DEATH
     * - final manual close (player `CloseReturn`) after a RETURN_READY contract
     *
     * In these cases the contract should no longer appear on the board, but we still want to retain
     * a completed snapshot for audit/debug/replay.
     *
     * ## When NOT to use this
     * Do **not** use this for in-place status transitions where the board entry must remain present.
     * For example:
     * - OPEN → LOCKED (taken)
     * - LOCKED → OPEN (future feature: unlock)
     *
     * For those transitions use [updateBoardStatus] (or other status-only helpers) because they do not
     * remove contracts from the board and do not create archive snapshots.
     *
     * ## State effects (pure / no mutation)
     * This function is pure and returns values that the caller should apply to state:
     * - the completed contract is removed from the returned `boards` list
     * - a completed snapshot (status=COMPLETED) is returned in a separate list for appending to
     *   `ContractState.archive`
     *
     * ## Determinism contract
     * - No RNG draws.
     * - No time / wall-clock.
     * - Result depends only on input arguments.
     *
     * @param boards Current board contracts.
     * @param boardIdToComplete ID of board to potentially complete.
     * @param activeContracts Current active contracts (for completion check).
     *
     * ## Return value semantics
     * @return Pair(remainingBoards, archivedBoards)
     * - `remainingBoards`: the input [boards] with [boardIdToComplete] removed (if completion occurred)
     * - `archivedBoards`: empty if no completion occurred, otherwise a single-element list containing a
     *   snapshot of the completed board contract (status=COMPLETED)
     *
     * The snapshot is returned directly (instead of requiring callers to re-query) because the board
     * entry will be removed as part of the same transition.
     */
    fun completeBoardAndExtract(
        boards: List<BoardContract>,
        boardIdToComplete: ContractId?,
        activeContracts: List<ActiveContract>
    ): Pair<List<BoardContract>, List<BoardContract>> {
        if (boardIdToComplete == null) return Pair(boards, emptyList())

        val boardContract = boards.firstOrNull { it.id == boardIdToComplete }
        val decision = shouldComplete(boardContract, activeContracts)

        if (!decision.shouldComplete || boardContract == null) return Pair(boards, emptyList())

        // Mark completed and export to archive
        val completed = boardContract.copy(status = BoardStatus.COMPLETED)
        val remaining = boards.filter { it.id != boardIdToComplete }
        return Pair(remaining, listOf(completed))
    }
}

/**
 * Result of board status check.
 */
data class BoardStatusDecision(
    /** Whether the board should be marked COMPLETED. */
    val shouldComplete: Boolean
)
