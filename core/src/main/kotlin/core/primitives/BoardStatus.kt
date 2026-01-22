package core.primitives

/**
 * ## Role
 * - Board entry lifecycle state from posting to completion. Enforces `INV_BOARD_ACTIVE_EXCLUSION` constraint: no `OPEN` board entry if an active contract exists for the same `contractId`.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class BoardStatus {
    /**
     * Contract available for taking; commands `UpdateContractTerms` and `CancelContract` require `status == OPEN`.
     */
    OPEN,

    /**
     * Contract locked; active contract exists for this `contractId`.
     */
    LOCKED,

    /**
     * Contract completed; terminal state.
     */
    COMPLETED
}
