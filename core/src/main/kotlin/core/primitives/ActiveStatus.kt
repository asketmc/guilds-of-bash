package core.primitives

/**
 * ## Role
 * - Lifecycle state of an active contract from execution to closure. Observed pipeline: `ContractTaken` → `WipAdvanced` → `ContractResolved` → `ReturnClosed`.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class ActiveStatus {
    /**
     * Contract is in active execution; `daysRemaining` decrements daily.
     */
    WIP,

    /**
     * Contract outcome resolved; awaits explicit closure.
     */
    RETURN_READY,

    /**
     * Return closed; contract removed from active set.
     */
    CLOSED
}
