// FILE: core/src/main/kotlin/core/pipeline/GuildProgression.kt
package core.pipeline

import core.RANK_THRESHOLDS
import core.calculateNextRank

/**
 * Guild progression decision model.
 *
 * ## Semantic Ownership
 * Answers exactly one question: **Why rank changed?**
 *
 * ## Stability Gradient
 * STABLE: Pure calculation logic with explicit thresholds.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT touch economy.
 * - Must NOT modify hero state.
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object GuildProgression {

    /**
     * Computes guild rank progression after completing a contract.
     *
     * @param currentCompletedContracts Total completed contracts before this one.
     * @param currentGuildRank Current guild rank ordinal.
     * @return [GuildProgressionResult] containing rank changes.
     */
    fun computeAfterCompletion(
        currentCompletedContracts: Int,
        currentGuildRank: Int
    ): GuildProgressionResult {
        val newCompleted = currentCompletedContracts + 1
        val (newRank, contractsForNext) = calculateNextRank(newCompleted, currentGuildRank)
        val rankChanged = newRank != currentGuildRank

        return GuildProgressionResult(
            newCompletedContractsTotal = newCompleted,
            newGuildRank = newRank,
            contractsForNextRank = contractsForNext,
            rankChanged = rankChanged,
            oldRank = currentGuildRank
        )
    }

    /**
     * Gets the inbox and heroes multipliers for a given rank.
     *
     * @param guildRank Current guild rank ordinal (1-7).
     * @return Pair of (inboxMultiplier, heroesMultiplier).
     */
    fun getRankMultipliers(guildRank: Int): Pair<Int, Int> {
        val rankOrdinal = guildRank.coerceIn(1, RANK_THRESHOLDS.size)
        val rankThreshold = RANK_THRESHOLDS.firstOrNull { it.rankOrdinal == rankOrdinal }
        val nInbox = rankThreshold?.inboxMultiplier ?: 1
        val nHeroes = rankThreshold?.heroesMultiplier ?: 1
        return Pair(nInbox, nHeroes)
    }
}

/**
 * Result of guild progression computation.
 */
data class GuildProgressionResult(
    /** New total completed contracts. */
    val newCompletedContractsTotal: Int,
    /** New guild rank ordinal. */
    val newGuildRank: Int,
    /** Contracts required for next rank. */
    val contractsForNextRank: Int,
    /** Whether rank changed. */
    val rankChanged: Boolean,
    /** Previous rank ordinal (for events). */
    val oldRank: Int
)
