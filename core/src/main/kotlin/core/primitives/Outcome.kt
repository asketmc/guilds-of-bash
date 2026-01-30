package core.primitives

/**
 * ## Role
 * - Contract resolution outcome affecting stability delta and metric S8. Target schema extends to include `MISSING` and `DEATH`.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class Outcome {
    /**
     * Contract completed successfully; full reward.
     */
    SUCCESS,

    /**
     * Contract partially completed; reduced reward.
     */
    PARTIAL,

    /**
     * Contract failed; no reward.
     */
    FAIL,

    /**
     * Hero died during contract resolution; treated economically like FAIL and hero is removed from roster.
     */
    DEATH,

    /**
     * Hero missing during contract resolution; narrative alias of DEATH for PoC/MVP scope.
     * Treated identically to DEATH in backend logic (roster removal, economy effects, invariants).
     */
    MISSING
}
