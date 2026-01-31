package core

data class RankThreshold(
    val rankOrdinal: Int,
    val contractsRequired: Int,
    val inboxMultiplier: Int,
    val heroesMultiplier: Int
)

val RANK_THRESHOLDS = listOf(
    RankThreshold(rankOrdinal = 1, contractsRequired = 0, inboxMultiplier = 1, heroesMultiplier = 1), // F
    RankThreshold(rankOrdinal = 2, contractsRequired = 10, inboxMultiplier = 2, heroesMultiplier = 2), // E
    RankThreshold(rankOrdinal = 3, contractsRequired = 30, inboxMultiplier = 3, heroesMultiplier = 3), // D
    RankThreshold(rankOrdinal = 4, contractsRequired = 60, inboxMultiplier = 4, heroesMultiplier = 4), // C
    RankThreshold(rankOrdinal = 5, contractsRequired = 100, inboxMultiplier = 5, heroesMultiplier = 5), // B
    RankThreshold(rankOrdinal = 6, contractsRequired = 150, inboxMultiplier = 6, heroesMultiplier = 6), // A
    RankThreshold(rankOrdinal = 7, contractsRequired = 250, inboxMultiplier = 7, heroesMultiplier = 7)  // S
)

/**
 * Given completedContracts total and current guildRank (int 1..7), returns Pair(newRankInt, contractsForNextRank)
 */
fun calculateNextRank(completedContracts: Int, _currentRankOrdinal: Int): Pair<Int, Int> {
    // Find highest threshold <= completedContracts
    val newRankThreshold = RANK_THRESHOLDS.lastOrNull { completedContracts >= it.contractsRequired } ?: RANK_THRESHOLDS.first()
    val newRank = newRankThreshold.rankOrdinal

    // Determine next threshold (contracts required for next rank)
    val nextThreshold = RANK_THRESHOLDS.firstOrNull { it.rankOrdinal > newRank } ?: RANK_THRESHOLDS.last()
    val contractsForNext = if (nextThreshold.rankOrdinal == newRank) Int.MAX_VALUE else nextThreshold.contractsRequired

    return Pair(newRank, contractsForNext)
}
