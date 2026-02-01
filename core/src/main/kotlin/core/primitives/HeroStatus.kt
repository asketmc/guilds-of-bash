package core.primitives

/**
 * Hero availability and disciplinary status.
 *
 * ## Role
 * - Determines hero eligibility for contract pickup (FG_03).
 * - Tracks disciplinary status from fraud investigation (WARNED, BANNED).
 *
 * ## Contract
 * - AVAILABLE: Hero can take contracts.
 * - ON_MISSION: Hero is on active contract, cannot take new contracts.
 * - WARNED: Hero flagged for fraud, can still take contracts (first offense).
 * - BANNED: Hero banned from contracts for repeated fraud.
 * - MISSING/DEAD: Terminal states from contract outcomes.
 *
 * ## Invariants
 * - BANNED heroes must not be assigned to contracts during pickup phase.
 * - WARNED status is informational; hero remains eligible for contracts.
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
     * Hero warned for suspected fraud (first offense).
     * Can still take contracts but a repeat offense leads to BAN.
     */
    WARNED,

    /**
     * Hero banned from contracts due to repeated fraud.
     * Cannot take new contracts until ban expires.
     */
    BANNED,

    /**
     * Hero missing; outcome `MISSING` reached.
     */
    MISSING,

    /**
     * Hero dead; outcome `DEATH` reached.
     */
    DEAD
}
