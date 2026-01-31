package core

import core.primitives.Rank

/**
 * Calculates the tax amount due based on guild rank.
 *
 * Higher-ranked guilds pay progressively more tax, reflecting their greater
 * economic activity and obligations to the realm.
 *
 * ## Contract
 * - Tax scales by rank: F=1x, E=2x, D=4x, C=8x, B=12x, A=16x, S=24x of base.
 *
 * ## Preconditions
 * - `guildRankOrdinal` in range 1..7 (clamped if out of bounds).
 * - `baseAmount >= 0`
 *
 * ## Postconditions
 * - Returns `baseAmount * multiplier` where multiplier depends on rank.
 *
 * ## Determinism
 * - Pure function; same inputs always produce same outputs.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 *
 * @param guildRankOrdinal Guild rank ordinal (1=F, 2=E, ..., 7=S).
 * @param baseAmount Base tax amount in copper.
 * @return Scaled tax amount in copper.
 */
fun calculateTaxAmount(guildRankOrdinal: Int, baseAmount: Int): Int {
    // guildRankOrdinal corresponds to Rank.ordinal + 1 in existing code (guildRank stored as Int)
    // Map ordinal-like int to Rank index (1 -> F, 2 -> E, ...)
    val idx = (guildRankOrdinal - 1).coerceIn(0, Rank.values().size - 1)
    val rank = Rank.values()[idx]

    return when (rank) {
        Rank.F -> baseAmount
        Rank.E -> baseAmount * 2
        Rank.D -> baseAmount * 4
        Rank.C -> baseAmount * 8
        Rank.B -> baseAmount * 12
        Rank.A -> baseAmount * 16
        Rank.S -> baseAmount * 24
    }
}
