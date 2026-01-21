// FILE: core/src/main/kotlin/core/state/RegionState.kt
package core.state

/**
 * Region simulation state.
 *
 * ## Contract
 * Holds world/region parameters that affect content scaling and systemic pressure (e.g., threat scaling).
 *
 * ## Invariants
 * - [stability] is expected to be bounded.
 *   Current PoC uses 0..100 and clamps updates into this range.
 *
 * ## Determinism
 * Pure data container. Deterministic evolution requires deterministic reducer logic.
 *
 * @property stability Regional stability score (expected range 0..100).
 */
data class RegionState(
    val stability: Int
)
