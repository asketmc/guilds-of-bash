/**
 * Weekly Gazette summary generation for console adapter.
 *
 * Deterministic newspaper-style block printed when state.day % 7 == 0 after AdvanceDay.
 * Uses a ring buffer to track the last 7 daily snapshots and computes deltas.
 */
package console

import core.DaySnapshot

/**
 * Snapshot data for gazette delta calculations.
 * Uses only primitive values for stable hashing and portability.
 */
data class GazetteSnapshot(
    val day: Int,
    val gold: Int,
    val trophies: Int,
    val stability: Int,
    val boardCount: Int,
    val activeCount: Int,
    val returnsCount: Int
) {
    companion object {
        fun fromDaySnapshot(snapshot: DaySnapshot): GazetteSnapshot = GazetteSnapshot(
            day = snapshot.day,
            gold = snapshot.money,
            trophies = snapshot.trophies,
            stability = snapshot.regionStability,
            boardCount = snapshot.boardCount,
            activeCount = snapshot.activeCount,
            returnsCount = snapshot.returnsNeedingCloseCount
        )
    }
}

/**
 * Ring buffer holding the last 7 daily snapshots for gazette computation.
 * Immutable: each add() returns a new instance.
 */
class GazetteBuffer private constructor(
    private val snapshots: List<GazetteSnapshot>
) {
    constructor() : this(emptyList())

    /**
     * Add a snapshot to the buffer, maintaining max 7 entries.
     * Returns a new buffer instance.
     */
    fun add(snapshot: GazetteSnapshot): GazetteBuffer {
        val newList = (snapshots + snapshot).takeLast(7)
        return GazetteBuffer(newList)
    }

    /**
     * Returns true if we have at least 7 days of data.
     */
    fun hasFullWeek(): Boolean = snapshots.size >= 7

    /**
     * Returns the number of snapshots in buffer.
     */
    fun size(): Int = snapshots.size

    /**
     * Get the oldest snapshot (first of the week).
     */
    fun oldest(): GazetteSnapshot? = snapshots.firstOrNull()

    /**
     * Get the newest snapshot (end of the week).
     */
    fun newest(): GazetteSnapshot? = snapshots.lastOrNull()

    /**
     * Calculate total returns count over the week.
     */
    fun totalReturns(): Int = snapshots.sumOf { it.returnsCount }
}

/**
 * Weekly gazette headline generator.
 * Produces deterministic headlines based on fixed rules.
 */
object GazetteHeadlines {
    /**
     * Generate 1-3 deterministic headlines based on weekly deltas.
     *
     * Rules:
     * - if Δstability ≤ -5 → unrest headline
     * - if Δgold ≥ +100 → prosperity headline
     * - if returnsCount > 0 → bureaucracy headline
     */
    fun generate(
        buffer: GazetteBuffer,
        currentSnapshot: GazetteSnapshot
    ): List<String> {
        val oldest = buffer.oldest() ?: return emptyList()
        val headlines = mutableListOf<String>()

        val stabilityDelta = currentSnapshot.stability - oldest.stability
        val goldDelta = currentSnapshot.gold - oldest.gold
        val hasReturns = buffer.totalReturns() > 0

        // Rule 1: Unrest headline if stability dropped significantly
        if (stabilityDelta <= -5) {
            headlines.add(unrestHeadline(stabilityDelta))
        }

        // Rule 2: Prosperity headline if gold increased significantly
        if (goldDelta >= 100) {
            headlines.add(prosperityHeadline(goldDelta))
        }

        // Rule 3: Bureaucracy headline if there were returns to process
        if (hasReturns) {
            headlines.add(bureaucracyHeadline())
        }

        // If no headlines triggered, add a neutral one
        if (headlines.isEmpty()) {
            headlines.add(neutralHeadline(currentSnapshot.day))
        }

        return headlines.take(3)
    }

    private fun unrestHeadline(delta: Int): String = when {
        delta <= -10 -> "CHAOS GRIPS THE REGION: Stability plummets as order crumbles!"
        delta <= -7 -> "GROWING UNREST: Citizens report increased bandit activity."
        else -> "TENSIONS RISE: Minor disturbances reported across the territory."
    }

    private fun prosperityHeadline(delta: Int): String = when {
        delta >= 500 -> "GOLDEN AGE DAWNS: Guild coffers overflow with unprecedented wealth!"
        delta >= 250 -> "PROSPERITY ABOUNDS: Merchants praise the guild's steady hand."
        else -> "TRADE FLOURISHES: Local economy shows healthy growth."
    }

    private fun bureaucracyHeadline(): String =
        "PAPERWORK PILES UP: Clerks struggle with returning hero documentation."

    private fun neutralHeadline(day: Int): String = when (day % 3) {
        0 -> "ALL QUIET: A peaceful week passes without major incident."
        1 -> "STEADY PROGRESS: Guild operations continue as normal."
        else -> "BUSINESS AS USUAL: The realm endures another uneventful week."
    }
}

/**
 * Renders the weekly gazette as an ASCII framed block.
 */
object GazetteRenderer {
    private const val GAZETTE_TITLE = "THE GUILD GAZETTE"

    /**
     * Render the weekly gazette block.
     * Returns null if conditions not met (not day 7, or insufficient data).
     */
    fun render(
        day: Int,
        buffer: GazetteBuffer,
        currentSnapshot: GazetteSnapshot
    ): List<String>? {
        // Only render on weekly boundary
        if (day % 7 != 0) return null

        // Need at least 7 days of data
        if (!buffer.hasFullWeek()) return null

        val oldest = buffer.oldest() ?: return null
        val headlines = GazetteHeadlines.generate(buffer, currentSnapshot)

        val weekRange = "Week ${(day / 7)}: Days ${oldest.day}-${currentSnapshot.day}"
        val rows = mutableListOf<String>()

        rows.add(weekRange)
        rows.add("")

        // Headlines section
        rows.add("HEADLINES:")
        for (headline in headlines) {
            rows.add("• $headline")
        }
        rows.add("")

        // Weekly summary stats
        val goldDelta = currentSnapshot.gold - oldest.gold
        val trophyDelta = currentSnapshot.trophies - oldest.trophies
        val stabilityDelta = currentSnapshot.stability - oldest.stability

        rows.add("WEEKLY SUMMARY:")
        rows.add("  Treasury: ${currentSnapshot.gold} copper ${formatDelta(goldDelta)}")
        rows.add("  Trophies: ${currentSnapshot.trophies} ${formatDelta(trophyDelta)}")
        rows.add("  Regional Stability: ${currentSnapshot.stability}% ${formatDelta(stabilityDelta)}")
        rows.add("  Active Contracts: ${currentSnapshot.activeCount}")
        rows.add("  Posted Contracts: ${currentSnapshot.boardCount}")

        return UiBox.render(GAZETTE_TITLE, rows)
    }

    private fun formatDelta(delta: Int): String = when {
        delta > 0 -> "(+$delta)"
        delta < 0 -> "($delta)"
        else -> "(±0)"
    }
}
