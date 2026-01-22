package core.primitives

/**
 * ## Role
 * - Trophy quality grade affecting `ProofRequirement.qualityMin` and stable ordering (quality asc by enum ordinal). Generation: qRoll → `OK` (<70), `DAMAGED` (<90), otherwise `NONE`.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class Quality {
    /**
     * Undamaged trophy; highest quality.
     */
    OK,

    /**
     * Damaged trophy; reduced quality.
     */
    DAMAGED,

    /**
     * No trophy or ruined quality; lowest quality. Maps to Target's `RUINED`.
     */
    NONE
}
