// FILE: core/src/main/kotlin/core/state/GameState.kt
package core.state

/**
 * Root immutable game state.
 *
 * ## Contract
 * Single source of truth for deterministic core logic. All state transitions are expected to be performed
 * by pure reducer functions producing a new [GameState] plus events.
 *
 * ## Invariants
 * - Sub-states must satisfy their own invariants ([MetaState], [GuildState], [RegionState], [EconomyState],
 *   [ContractState], [HeroState]).
 * - Cross-substate references are expected to be valid:
 *   - Contract references to heroes and board contracts should match entities in [heroes] and [contracts.board].
 * - Day-index consistency: [meta.dayIndex] is the authoritative day counter used by time-based fields.
 *
 * ## Determinism
 * Intended to be deterministic given:
 * - deterministic reducer logic
 * - deterministic RNG stream derived from [MetaState.seed]
 * - stable canonical serialization for hashing/persistence
 *
 * @property meta Meta information and monotonic counters (seed/day/revision/id counters, tax schedule).
 * @property guild Guild progression and policies (rank/reputation/proof policy).
 * @property region Regional simulation parameters (e.g., stability).
 * @property economy Currency and inventory counters (money/escrow/trophies).
 * @property contracts Contract lifecycle aggregates (inbox/board/active/returns).
 * @property heroes Hero roster and day-scoped arrivals.
 */
data class GameState(
    val meta: MetaState,
    val guild: GuildState,
    val region: RegionState,
    val economy: EconomyState,
    val contracts: ContractState,
    val heroes: HeroState
)
