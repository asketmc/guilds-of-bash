// FILE: core/src/main/kotlin/core/invariants/InvariantId.kt
/**
 * Invariant identifiers emitted by the invariants verifier.
 *
 * ## Contract
 * - Each enum entry is a stable identifier for one invariant check.
 * - Intended for machine-readable categorization and deterministic reporting.
 *
 * ## Invariants
 * - `code == name`.
 */
package core.invariants


enum class InvariantId {
    // IDs
    IDS__NEXT_CONTRACT_ID_POSITIVE,
    IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE,
    IDS__NEXT_HERO_ID_POSITIVE,
    IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID,
    IDS__NEXT_ACTIVE_CONTRACT_ID_GT_MAX_ACTIVE_ID,
    IDS__NEXT_HERO_ID_GT_MAX_HERO_ID,


    // Contracts
    CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE,
    CONTRACTS__RETURN_READY_HAS_RETURN_PACKET,
    CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE,
    CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE,
    CONTRACTS__WIP_DAYS_REMAINING_IN_1_2,


    // Heroes
    HEROES__ON_MISSION_IN_EXACTLY_ONE_ACTIVE_CONTRACT,
    HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION,


    // Economy/Region
    ECONOMY__MONEY_NON_NEGATIVE,
    ECONOMY__TROPHIES_NON_NEGATIVE,
    ECONOMY__RESERVED_NON_NEGATIVE,
    ECONOMY__AVAILABLE_NON_NEGATIVE,
    REGION__STABILITY_0_100,
    GUILD__REPUTATION_0_100;


    /**
     * Stable string representation of this invariant id.
     *
     * ## Contract
     * - Returns `name` (no transformation).
     *
     * ## Determinism
     * - Pure and stable for a given enum entry.
     */
    val code: String
        get() = name
}