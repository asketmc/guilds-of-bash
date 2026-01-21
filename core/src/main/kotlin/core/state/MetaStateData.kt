// FILE: core/src/main/kotlin/core/state/MetaStateData.kt
package core.state

/**
 * Meta information for determinism, persistence, and monotonic counters.
 *
 * ## Contract
 * Stores run-level metadata and counters:
 * - save format versioning
 * - deterministic seed
 * - day/revision counters
 * - id generators for entities
 * - tax schedule and outstanding amounts
 *
 * ## Invariants
 * - [saveVersion] is a format version (expected >= 1).
 * - [dayIndex] is a day-index (expected >= 0).
 * - [revision] is a monotonic per-command counter (expected >= 0, strictly increasing for accepted commands).
 * - [ids] counters are expected to be non-negative and monotonic.
 * - Tax values are expected to be non-negative:
 *   - [taxAmountDue] (copper units, >= 0)
 *   - [taxPenalty] (copper units, >= 0)
 *   - [taxMissedCount] (count, >= 0)
 * - [taxDueDay] is a day-index (expected >= 0; may be less/greater than [dayIndex] depending on scheduling logic).
 *
 * ## Determinism
 * [seed] is the authoritative root for deterministic RNG streams. Given the same [seed] and the same command stream,
 * the reducer should produce the same state evolution and events.
 *
 * @property saveVersion Save format version (>= 1).
 * @property seed Unsigned 32-bit RNG seed for deterministic runs.
 * @property dayIndex Current day-index (>= 0).
 * @property revision Monotonic command-applied revision counter (>= 0).
 * @property ids Monotonic id generators for contracts/heroes/actives.
 * @property taxDueDay Day-index when tax is next due (>= 0).
 * @property taxAmountDue Outstanding tax principal in copper currency units (>= 0).
 * @property taxPenalty Outstanding tax penalty in copper currency units (>= 0).
 * @property taxMissedCount Number of consecutive missed tax due dates (count, >= 0).
 */
data class MetaState(
    val saveVersion: Int,
    val seed: UInt,
    val dayIndex: Int,
    val revision: Long,
    val ids: IdCounters,

    // Tax system (Phase 2)
    val taxDueDay: Int,
    val taxAmountDue: Int,
    val taxPenalty: Int,
    val taxMissedCount: Int
)

/**
 * Monotonic id counters owned by [MetaState].
 *
 * ## Contract
 * Provides the next ids to allocate for entity creation. Allocation is expected to:
 * - read the current nextX value
 * - assign it to a new entity
 * - increment nextX by 1
 *
 * ## Invariants
 * - All counters are expected to be >= 0.
 * - Counters are expected to be monotonic (never decrease) across accepted state transitions.
 *
 * ## Determinism
 * Deterministic as long as allocation order is deterministic for a given command stream.
 *
 * @property nextContractId Next [ContractId] value (monotonic id counter, >= 0).
 * @property nextHeroId Next [HeroId] value (monotonic id counter, >= 0).
 * @property nextActiveContractId Next [ActiveContractId] value (monotonic id counter, >= 0).
 */
data class IdCounters(
    val nextContractId: Int,
    val nextHeroId: Int,
    val nextActiveContractId: Int
)
