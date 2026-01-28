//file main/kotlin/core/rng/Rng
package core.rng

import java.util.SplittableRandom

/**
 * Deterministic pseudo-random generator wrapper used by the core to make RNG usage auditable.
 *
 * ## Contract
 * - Produces pseudo-random values derived only from the provided `seed`.
 * - Increments `draws` by exactly 1 for each successful call to any `next*` method on this wrapper.
 *
 * ## Invariants
 * - `draws` is monotonic non-decreasing and starts at 0 for a new instance.
 * - `draws` reflects only calls performed via this wrapper (not external access to the underlying PRNG).
 *
 * ## Determinism
 * - For the same `seed` and the same sequence of wrapper calls, the produced outputs and `draws` values are identical.
 *
 * @param seed Seed value used to initialize the underlying PRNG; units: 64-bit seed value.
 * @property draws Monotonic counter of RNG draws performed through this wrapper; units: draw-count (>= 0).
 */
open class Rng(seed: Long) {
    private val random = SplittableRandom(seed)

    var draws: Long = 0
        private set

    /**
     * ## Contract
     * - Returns a pseudo-random `Int` in the range `[0, bound)`.
     *
     * ## Preconditions
     * - `bound > 0` (delegates to the underlying PRNG contract).
     *
     * ## Postconditions
     * - `draws` is incremented by 1.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Deterministic for a fixed `seed` and call order.
     *
     * ## Complexity
     * - Time: O(1)
     * - Memory: O(1)
     *
     * @param bound Exclusive upper bound; units: count; range: `> 0`.
     * @return Pseudo-random `Int` in `[0, bound)`.
     */
    open fun nextInt(bound: Int): Int {
        draws++
        return random.nextInt(bound)
    }

    /**
     * ## Contract
     * - Returns a pseudo-random `Long` in the range `[0, bound)`.
     *
     * ## Preconditions
     * - `bound > 0` (delegates to the underlying PRNG contract).
     *
     * ## Postconditions
     * - `draws` is incremented by 1.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Deterministic for a fixed `seed` and call order.
     *
     * ## Complexity
     * - Time: O(1)
     * - Memory: O(1)
     *
     * @param bound Exclusive upper bound; units: count; range: `> 0`.
     * @return Pseudo-random `Long` in `[0, bound)`.
     */
    open fun nextLong(bound: Long): Long {
        draws++
        return random.nextLong(bound)
    }

    /**
     * ## Contract
     * - Returns a pseudo-random boolean value.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - `draws` is incremented by 1.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Deterministic for a fixed `seed` and call order.
     *
     * ## Complexity
     * - Time: O(1)
     * - Memory: O(1)
     *
     * @return Pseudo-random boolean.
     */
    open fun nextBoolean(): Boolean {
        draws++
        return random.nextBoolean()
    }

    /**
     * ## Contract
     * - Returns a pseudo-random `Double` in the range `[0.0, 1.0)`.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - `draws` is incremented by 1.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Deterministic for a fixed `seed` and call order.
     *
     * ## Complexity
     * - Time: O(1)
     * - Memory: O(1)
     *
     * @return Pseudo-random `Double` in `[0.0, 1.0)`.
     */
    open fun nextDouble(): Double {
        draws++
        return random.nextDouble()
    }
}
