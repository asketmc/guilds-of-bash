package core.primitives

/**
 * Proof validation and fraud investigation policy.
 *
 * ## Role
 * Controls proof strictness for return closure and investigation accuracy for fraud detection.
 *
 * ## Contract
 * - Affects return closure eligibility (STRICT blocks damaged/suspicious returns).
 * - Affects fraud investigation catch probability and rumor spread probability.
 * - SOFT: lenient closure, lower catch chance, higher rumor chance on escape.
 * - FAST: lenient closure, no fraud investigation (legacy behavior).
 * - STRICT: strict closure, higher catch chance, lower rumor chance on escape.
 *
 * ## Invariants
 * - SOFT and STRICT are the active fraud investigation policies.
 * - FAST is retained for backward compatibility but does not trigger fraud investigation.
 *
 * ## Determinism
 * - Policy selection is deterministic; probabilities are used with seeded RNG.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class ProofPolicy {
    /**
     * Soft policy: lenient proof validation.
     * - Closure: allowed even for damaged/suspicious returns.
     * - Investigation: lower catch chance (50%), higher rumor chance on escape (50%).
     */
    SOFT,

    /**
     * Fast proof validation; lenient rules (legacy, no fraud investigation).
     * - Closure: allowed always.
     * - Investigation: not triggered.
     */
    FAST,

    /**
     * Strict proof validation; rigorous rules.
     * - Closure: blocked for damaged quality or suspected theft.
     * - Investigation: higher catch chance (90%), lower rumor chance on escape (10%).
     */
    STRICT
}
