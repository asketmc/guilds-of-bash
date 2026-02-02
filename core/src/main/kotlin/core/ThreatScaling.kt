package core

import core.rng.Rng

/**
 * Computes the regional threat level based on stability.
 *
 * Lower stability means the region is more dangerous, producing higher threat levels
 * that affect contract difficulty generation.
 *
 * ## Contract
 * - Returns 0..3 based on stability thresholds: >=80→3, >=60→2, >=40→1, else→0.
 *
 * ## Preconditions
 * - `stability` should be in 0..100 range.
 *
 * ## Postconditions
 * - Returns threat level in range 0..3.
 *
 * ## Determinism
 * - Pure function; same stability always produces same threat.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 *
 * @param stability Regional stability score (0..100).
 * @return Threat level (0=low, 3=high).
 */
fun calculateThreatLevel(stability: Int): Int {
    return when {
        stability >= 80 -> 3
        stability >= 60 -> 2
        stability >= 40 -> 1
        else -> 0
    }
}

/**
 * Computes base difficulty for a new contract draft.
 *
 * Difficulty increases with threat level and includes random variance.
 *
 * ## Contract
 * - Returns `1 + threatLevel + variance` where variance is 0 or 1.
 *
 * ## Preconditions
 * - `threatLevel >= 0`
 *
 * ## Postconditions
 * - Returns difficulty >= 1.
 *
 * ## Determinism
 * - Deterministic given same RNG state.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 *
 * @param threatLevel Current regional threat level (0..3).
 * @param rng Deterministic RNG for variance.
 * @return Base difficulty value (>= 1).
 */
fun calculateBaseDifficulty(threatLevel: Int, rng: Rng): Int {
    val base = 1
    val threatBonus = threatLevel
    val variance = rng.nextInt(2) // 0 or 1
    return base + threatBonus + variance
}
