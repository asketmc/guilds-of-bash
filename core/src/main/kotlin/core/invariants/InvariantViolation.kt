package core.invariants


/**
 * Single invariant violation record produced by `verifyInvariants`.
 *
 * ## Contract
 * - Identifies the violated invariant via `invariantId`.
 * - Provides a deterministic, machine-readable `details` payload describing:
 * - the expected condition
 * - the observed values (when applicable)
 *
 * ## Invariants
 * - None.
 *
 * @property invariantId Stable identifier of the violated invariant.
 * @property details Deterministic diagnostic string describing the failure.
 */
data class InvariantViolation(
    val invariantId: InvariantId,
    val details: String
)