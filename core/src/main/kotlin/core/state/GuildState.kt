// FILE: core/src/main/kotlin/core/state/GuildState.kt
package core.state

/**
 * Guild progression, policy state, and pending reputation changes.
 *
 * ## Contract
 * Tracks player-facing guild progression (rank/reputation) and governance policies that affect reducer behavior.
 * Also tracks pending reputation changes from fraud rumors that are applied on weekly boundaries.
 *
 * ## Invariants
 * - [guildRank] is a rank ordinal (expected >= 1; current PoC commonly maps 1..7 to F..S tiers).
 * - [completedContractsTotal] is a lifetime counter (expected >= 0).
 * - [contractsForNextRank] is a threshold value (expected >= 0; may be Int.MAX_VALUE when maxed).
 * - [reputation] is a points counter (expected >= 0; upper bound is implementation-defined).
 * - [pendingReputationDelta] is an accumulator for deferred reputation changes (can be negative).
 *
 * ## Fraud System Integration
 * - [pendingReputationDelta] accumulates reputation penalties from fraud rumors.
 * - Penalties are scheduled via rumor events and applied on weekly boundary.
 * - This ensures reputation changes are predictable and auditable.
 *
 * ## Determinism
 * Pure data container. Deterministic updates require deterministic reducer logic.
 *
 * @property guildRank Rank ordinal (expected >= 1; tier mapping is implementation-defined).
 * @property reputation Reputation points (expected >= 0; scale is implementation-defined).
 * @property completedContractsTotal Lifetime number of completed contracts (count, >= 0).
 * @property contractsForNextRank Required completed contract count to reach the next rank (count, >= 0).
 * @property proofPolicy Policy controlling proof validation behavior (e.g., strict vs soft vs fast).
 * @property pendingReputationDelta Accumulated reputation delta from rumors (applied on weekly boundary).
 */
data class GuildState(
    val guildRank: Int,
    val reputation: Int,

    // Rank progression (Phase 2)
    val completedContractsTotal: Int,
    val contractsForNextRank: Int,

    // Proof validation policy (Phase 3)
    val proofPolicy: core.primitives.ProofPolicy = core.primitives.ProofPolicy.FAST,

    // Fraud system: pending reputation delta (applied on weekly boundary)
    val pendingReputationDelta: Int = 0
)
