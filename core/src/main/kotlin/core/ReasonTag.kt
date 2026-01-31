package core

/**
 * Diagnostic reason tags explaining why a contract outcome occurred.
 *
 * ## Role
 * - Attached to `ContractResolved` events to explain contributing factors.
 * - Enables analytics and player feedback on failure causes.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/analytics
 */
enum class ReasonTag {
    /** No specific reason identified. */
    UNKNOWN,
    /** Hero rank was below contract difficulty. */
    UNDERLEVELED,
    /** Hero equipment was insufficient for the task. */
    BAD_EQUIPMENT,
    /** Zero-fee contract with inadequate preparation time. */
    ZERO_FEE_LOW_PREP,
    /** Random ambush event during mission. */
    AMBUSH,
    /** Party members had conflicting traits or goals. */
    PARTY_CONFLICT
}
