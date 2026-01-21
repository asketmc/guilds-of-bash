// FILE: core/src/main/kotlin/core/state/EconomyState.kt
package core.state

/**
 * Economy-related state owned by the core reducer.
 *
 * ## Contract
 * Stores the guild's liquid funds and inventory counters used by commands and reward distribution.
 *
 * ## Invariants
 * - All numeric fields are expected to be non-negative.
 * - [reservedCopper] represents escrow/commitments and is expected to satisfy `0 <= reservedCopper <= moneyCopper`
 *   in solvency-preserving flows.
 *
 * ## Determinism
 * Pure data container. Determinism is defined by the producer (reducer/commands).
 *
 * @property moneyCopper Current liquid money in copper currency units (>= 0).
 * @property trophiesStock Current trophy inventory held by the guild (count, >= 0).
 * @property reservedCopper Escrowed/committed copper (currency units, >= 0; expected <= [moneyCopper]).
 */
data class EconomyState(
    val moneyCopper: Int,
    val trophiesStock: Int,
    val reservedCopper: Int
)
