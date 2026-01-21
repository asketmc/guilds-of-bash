package core.invariants

data class InvariantViolation(
    val invariantId: InvariantId,
    val details: String
)
