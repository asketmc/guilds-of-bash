package test

import core.rng.Rng
import org.junit.jupiter.api.Test
import test.helpers.TestLog

class RngSeedFinder {
    @Test
    fun `find seeds that produce GOOD NEUTRAL BAD buckets`() {
        // We need to account for 2 RNG draws in InboxGenerated (2x baseDifficulty)
        // Then the 3rd draw should be the bucket

        TestLog.log("Searching for seeds...")
        var goodSeed: Long? = null
        var neutralSeed: Long? = null
        var badSeed: Long? = null

        for (seed in 1L..10000L) {
            val rng = Rng(seed)

            // Simulate InboxGenerated: 2 draws for baseDifficulty
            rng.nextInt(2)  // First draft difficulty variance
            rng.nextInt(2)  // Second draft difficulty variance

            // Now check what bucket we get
            val bucket = rng.nextInt(3)

            when (bucket) {
                0 -> if (goodSeed == null) { goodSeed = seed; TestLog.log("GOOD seed: $seed") }
                1 -> if (neutralSeed == null) { neutralSeed = seed; TestLog.log("NEUTRAL seed: $seed") }
                2 -> if (badSeed == null) { badSeed = seed; TestLog.log("BAD seed: $seed") }
            }

            if (goodSeed != null && neutralSeed != null && badSeed != null) break
        }

        TestLog.log("\nFinal results:")
        TestLog.log("GOOD seed: $goodSeed")
        TestLog.log("NEUTRAL seed: $neutralSeed")
        TestLog.log("BAD seed: $badSeed")
    }
}
