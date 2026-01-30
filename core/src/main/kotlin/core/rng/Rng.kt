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
        val v = random.nextInt(bound)
        RngTrace.emit(
            RngTrace.Entry(
                drawIndex = draws - 1,
                method = "nextInt",
                bound = bound.toLong(),
                value = v.toLong()
            )
        )
        return v
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
        val v = random.nextLong(bound)
        RngTrace.emit(
            RngTrace.Entry(
                drawIndex = draws - 1,
                method = "nextLong",
                bound = bound,
                value = v
            )
        )
        return v
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
        val v = random.nextBoolean()
        RngTrace.emit(
            RngTrace.Entry(
                drawIndex = draws - 1,
                method = "nextBoolean",
                bound = null,
                value = if (v) 1 else 0
            )
        )
        return v
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
        val v = random.nextDouble()
        // Keep stable textual value by capturing raw bits.
        RngTrace.emit(
            RngTrace.Entry(
                drawIndex = draws - 1,
                method = "nextDouble",
                bound = null,
                value = java.lang.Double.doubleToRawLongBits(v)
            )
        )
        return v
    }
}

/**
 * Optional, zero-impact RNG trace hook.
 *
 * Contract:
 * - If [sink] is null, RNG behavior and performance are unchanged (aside from a single null check).
 * - The sink is invoked exactly once per successful next* call and receives the produced value.
 * - The sink must be side-effect safe and MUST NOT call back into this RNG.
 */
object RngTrace {
    @Volatile
    var sink: ((entry: Entry) -> Unit)? = null

    data class Entry(
        val drawIndex: Long,
        val method: String,
        val bound: Long?,
        val value: Long
    )

    fun emit(entry: Entry) {
        sink?.invoke(entry)
    }
}
