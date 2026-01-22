package core.primitives

/**
 * ## Role
 * - Proof strictness policy for future feature (FG_12). Not implemented in PoC scope; deferred to MVP.
 *
 * ## Stability
 * - Stable API: no; Audience: internal
 */
enum class ProofPolicy {
    /**
     * Fast proof validation; lenient rules (future feature).
     */
    FAST,

    /**
     * Strict proof validation; rigorous rules (future feature).
     */
    STRICT
}
