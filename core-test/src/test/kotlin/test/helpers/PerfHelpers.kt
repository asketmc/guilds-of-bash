// Split from TestHelpers.kt — perf utilities (P3/manual only)
package test.helpers

import core.Command
import core.CommandRejected
import core.InvariantViolated
import core.rng.Rng
import core.state.GameState
import core.state.initialState
import core.step

// Perf helpers (P3 / manual runs)
// NOTE: stores per-step times in memory; 1_000_000 steps ~= 8MB (ok). Much larger -> consider sampling.

data class PerfStats(
    val label: String,
    val steps: Int,
    val nanosTotal: Long,
    val nanosAvg: Long,
    val nanosP50: Long,
    val nanosP95: Long,
    val nanosMax: Long,
    val eventsTotal: Long,
    val invariantViolations: Long,
    val rejections: Long,
    val rngDraws: Long
)

private fun percentile(sorted: List<Long>, p: Double): Long {
    if (sorted.isEmpty()) return 0L
    val idx = ((sorted.size - 1) * p).toInt().coerceIn(0, sorted.size - 1)
    return sorted[idx]
}

fun runPerfLoop(
    label: String,
    stateSeed: UInt,
    rngSeed: Long,
    steps: Int,
    warmup: Int = -1,
    cmdFactory: (state: GameState, cmdId: Long, i: Int) -> Command
): PerfStats {
    var state = initialState(stateSeed)
    val rng = Rng(rngSeed)
    var cmdId = 1L

    val computed = steps / 10
    val realWarmup = when {
        warmup >= 0 -> warmup
        computed <= 0 -> 0
        computed >= 200 -> 200
        else -> computed
    }

    repeat(realWarmup) { i ->
        val cmd = cmdFactory(state, cmdId++, i)
        state = step(state, cmd, rng).state
    }

    val times = LongArray(steps)
    var eventsTotal = 0L
    var invTotal = 0L
    var rejTotal = 0L

    repeat(steps) { i ->
        val cmd = cmdFactory(state, cmdId++, i)

        val t0 = System.nanoTime()
        val res = step(state, cmd, rng)
        val t1 = System.nanoTime()

        times[i] = (t1 - t0)
        state = res.state

        eventsTotal += res.events.size.toLong()
        invTotal += res.events.count { it is InvariantViolated }.toLong()
        rejTotal += res.events.count { it is CommandRejected }.toLong()
    }

    val total = times.sum()
    val avg = if (steps == 0) 0L else total / steps
    val sorted = times.asList().sorted()

    return PerfStats(
        label = label,
        steps = steps,
        nanosTotal = total,
        nanosAvg = avg,
        nanosP50 = percentile(sorted, 0.50),
        nanosP95 = percentile(sorted, 0.95),
        nanosMax = sorted.lastOrNull() ?: 0L,
        eventsTotal = eventsTotal,
        invariantViolations = invTotal,
        rejections = rejTotal,
        rngDraws = rng.draws
    )
}

fun printPerf(stats: PerfStats) {
    fun nsToMs(ns: Long) = ns / 1_000_000.0
    val avgUs = stats.nanosAvg / 1_000.0
    println(
        """
        ── PERF: ${stats.label} ──
        steps=${stats.steps}
        total=${nsToMs(stats.nanosTotal)} ms
        avg=${avgUs} µs  p50=${stats.nanosP50 / 1_000.0} µs  p95=${stats.nanosP95 / 1_000.0} µs  max=${stats.nanosMax / 1_000.0} µs
        eventsTotal=${stats.eventsTotal}  inv=${stats.invariantViolations}  rej=${stats.rejections}
        rngDraws=${stats.rngDraws}
        """.trimIndent()
    )
}
