package test

import core.rng.Rng
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Contract tests for [core.rng.Rng].
 *
 * These tests check only stable invariants (ranges + draw counter)
 * and do not assert any specific pseudo-random sequence.
 */
class RngContractTest {

    @Test
    fun `draws increments for every next call`() {
        val rng = Rng(seed = 123L)
        assertEquals(0L, rng.draws)

        rng.nextInt(1)
        assertEquals(1L, rng.draws)

        rng.nextLong(1)
        assertEquals(2L, rng.draws)

        rng.nextBoolean()
        assertEquals(3L, rng.draws)

        rng.nextDouble()
        assertEquals(4L, rng.draws)
    }

    @Test
    fun `bounded and ranged methods satisfy basic contracts`() {
        val rng = Rng(seed = 999L)

        // For bound == 1, the only valid value is 0.
        assertEquals(0, rng.nextInt(1))
        assertEquals(0L, rng.nextLong(1))

        val d = rng.nextDouble()
        assertTrue(d >= 0.0, "nextDouble() must be >= 0.0 but was $d")
        assertTrue(d < 1.0, "nextDouble() must be < 1.0 but was $d")

        // We called 3 next* methods above.
        assertEquals(3L, rng.draws)
    }
}