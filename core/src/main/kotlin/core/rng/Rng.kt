package core.rng

import java.util.SplittableRandom

class Rng(seed: Long) {
    private val random = SplittableRandom(seed)

    var draws: Long = 0
        private set

    fun nextInt(bound: Int): Int {
        draws++
        return random.nextInt(bound)
    }

    fun nextLong(bound: Long): Long {
        draws++
        return random.nextLong(bound)
    }

    fun nextBoolean(): Boolean {
        draws++
        return random.nextBoolean()
    }

    fun nextDouble(): Double {
        draws++
        return random.nextDouble()
    }
}
