package core.primitives

/**
 * ## Role
 * - Hero availability state participating in contract taking eligibility (FG_03). Target outcomes `MISSING` and `DEATH` map to `MISSING` and `DEAD` status values.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class HeroStatus {
    /**
     * Hero available for contract assignment.
     */
    AVAILABLE,

    /**
     * Hero assigned to active contract.
     */
    ON_MISSION,

    /**
     * Hero missing; outcome `MISSING` reached.
     */
    MISSING,

    /**
     * Hero dead; outcome `DEATH` reached.
     */
    DEAD
}
