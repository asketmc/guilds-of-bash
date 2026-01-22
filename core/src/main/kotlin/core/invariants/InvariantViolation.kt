// FILE: core/src/main/kotlin/core/invariants/InvariantViolation.kt
package core.invariants

/**
 * Single invariant violation record produced by `verifyInvariants`.
 *
 * Contract:
 * - `invariantId` identifies which invariant failed.
 * - `details` is a deterministic diagnostic payload constructed solely from observed state values.
 * - The pair (`invariantId`, `details`) is intended to be stable for a given state snapshot.
 *
 * Invariants:
 * - `details` must be non-blank.
 */
data class InvariantViolation(
    val invariantId: InvariantId,
    val details: String
) {
    init {
        require(details.isNotBlank()) { "InvariantViolation.details must be non-blank" }
    }
}
