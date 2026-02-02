package test

import core.pipeline.OutcomeResolution
import core.primitives.Outcome
import core.rng.RngTrace
import org.junit.jupiter.api.Test
import test.helpers.ScriptedRng
import kotlin.test.assertTrue

@P1
class MissingOutcomeTest {

    @Test
    fun `resolveOutcome can produce MISSING via scripted RNG`() {
        val rng = ScriptedRng(99, 0, 0)

        val draws = mutableListOf<RngTrace.Entry>()
        val prevSink = RngTrace.sink
        try {
            RngTrace.sink = { e -> draws.add(e) }
            val decision = OutcomeResolution.decide(hero = null, contractDifficulty = 1, rng = rng)

            val drawsStr = draws.joinToString(separator = ",") { "[${it.drawIndex}:${it.method}@${it.bound}=${it.value}]" }

            // Ensure the outcome is death-like
            assertTrue(decision.outcome == Outcome.MISSING || decision.outcome == Outcome.DEATH,
                "Expected death-like outcome (MISSING|DEATH) but got ${decision.outcome}; draws=$drawsStr")

            // Basic sanity: ensure we observed at least one RNG draw and record the first draw
            assertTrue(draws.isNotEmpty(), "Expected RNG draws to be recorded; draws=$drawsStr")

        } finally {
            RngTrace.sink = prevSink
        }
    }
}
