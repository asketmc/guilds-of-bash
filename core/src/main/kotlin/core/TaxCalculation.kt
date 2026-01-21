package core

import core.primitives.Rank

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
