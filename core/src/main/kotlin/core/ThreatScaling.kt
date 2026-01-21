package core

import core.rng.Rng

fun calculateThreatLevel(stability: Int): Int {
    return when {
        stability >= 80 -> 3
        stability >= 60 -> 2
        stability >= 40 -> 1
        else -> 0
    }
}

fun calculateBaseDifficulty(threatLevel: Int, rng: Rng): Int {
    val base = 1
    val threatBonus = threatLevel
    val variance = rng.nextInt(2) // 0 or 1
    return base + threatBonus + variance
}
