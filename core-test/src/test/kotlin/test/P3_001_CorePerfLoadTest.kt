package test

import core.*
import core.primitives.SalvagePolicy
import core.state.GameState
import kotlin.test.Test
import org.junit.jupiter.api.Tag

/**
 * P3: performance/load smoke (manual only).
 *
 * Characteristics:
 * - Environment-dependent (JIT / CPU / GC)
 * - Potentially slow and memory-heavy
 * - Explicitly excluded from default CI test tasks
 *
 * Execution:
 * - ./gradlew core-test:perfTest
 * - Optional tuning via JVM properties:
 *   -Dperf.steps=<int>
 *   -Dperf.warmup=<int>
 */
@Tag("perf")
class P3_001_CorePerfLoadTest {

    private val stateSeed = 42u
    private val rngSeed = 100L

    private fun steps(): Int = System.getProperty("perf.steps")?.toIntOrNull() ?: 10_000
    private fun warmup(): Int = System.getProperty("perf.warmup")?.toIntOrNull() ?: 300

    @Test
    fun `perf - AdvanceDay baseline vs one-quest-per-day vs post-all`() {
        val n = steps()
        val w = warmup()

        // 1) BASELINE: AdvanceDay only
        val baseline = runPerfLoop(
            label = "baseline: AdvanceDay only",
            stateSeed = stateSeed,
            rngSeed = rngSeed,
            steps = n,
            warmup = w
        ) { _, cmdId, _ ->
            AdvanceDay(cmdId)
        }
        printPerf(baseline)

        // 2) STEADY: alternate Day / Post(1)
        val oneQuest = runPerfLoop(
            label = "steady: Day + Post(1) alternating",
            stateSeed = stateSeed,
            rngSeed = rngSeed,
            steps = n,
            warmup = w
        ) { state: GameState, cmdId, i ->
            if (i % 2 == 0) {
                AdvanceDay(cmdId)
            } else {
                val draft = state.contracts.inbox.firstOrNull()
                if (draft == null) {
                    AdvanceDay(cmdId)
                } else {
                    PostContract(
                        inboxId = draft.id.value.toLong(),
                        fee = 0,
                        salvage = SalvagePolicy.HERO,
                        cmdId = cmdId
                    )
                }
            }
        }
        printPerf(oneQuest)

        // 3) HEAVY: alternate Day / Post(high frequency)
        val postAll = runPerfLoop(
            label = "heavy: Day + Post(all inbox) alternating",
            stateSeed = stateSeed,
            rngSeed = rngSeed,
            steps = n,
            warmup = w
        ) { state: GameState, cmdId, i ->
            if (i % 2 == 0) {
                AdvanceDay(cmdId)
            } else {
                val draft = state.contracts.inbox.firstOrNull()
                if (draft == null) {
                    AdvanceDay(cmdId)
                } else {
                    PostContract(
                        inboxId = draft.id.value.toLong(),
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        cmdId = cmdId
                    )
                }
            }
        }
        printPerf(postAll)

        val r1 = oneQuest.nanosAvg.toDouble() / baseline.nanosAvg.toDouble()
        val r2 = postAll.nanosAvg.toDouble() / baseline.nanosAvg.toDouble()
        println("RATIO oneQuest/baseline = $r1")
        println("RATIO heavy/baseline    = $r2")
    }
}
