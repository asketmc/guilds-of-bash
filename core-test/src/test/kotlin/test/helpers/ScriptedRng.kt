package test.helpers

import core.rng.Rng
import core.rng.RngTrace

/**
 * Test-only RNG that returns scripted values in sequence.
 * Used to force specific outcomes in tests without relying on magic seeds.
 *
 * @param scriptedValues Sequence of values to return from nextInt() calls
 */
class ScriptedRng(private vararg val scriptedValues: Int) : Rng(0L) {
    private var callIndex = 0

    override fun nextInt(bound: Int): Int {
        if (callIndex >= scriptedValues.size) {
            throw IllegalStateException(
                "ScriptedRng exhausted: attempted to call nextInt($bound) but only ${scriptedValues.size} values were scripted. " +
                    "Call index: $callIndex"
            )
        }

        val value = scriptedValues[callIndex]

        // Validate the value is within bounds
        if (value >= bound) {
            throw IllegalArgumentException(
                "Scripted value $value is out of bounds for nextInt($bound). " +
                    "Value must be in [0, $bound). Call index: $callIndex"
            )
        }

        // Emit a trace entry so tests inspecting RngTrace.sink can observe draws.
        // We cannot increment the protected draws counter (private setter), so use callIndex as drawIndex proxy.
        try {
            RngTrace.emit(
                RngTrace.Entry(
                    drawIndex = callIndex.toLong(),
                    method = "nextInt",
                    bound = bound.toLong(),
                    value = value.toLong()
                )
            )
        } catch (t: Throwable) {
            // Swallow any trace sink errors in test helper to avoid affecting tests that don't use the trace
        }

        callIndex++
        return value
    }

    override fun nextLong(bound: Long): Long {
        return nextInt(bound.toInt()).toLong()
    }

    override fun nextBoolean(): Boolean {
        return nextInt(2) == 1
    }
}
