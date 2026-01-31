package core

import core.state.Hero
import core.primitives.HeroClass

/**
 * Computes the effective combat power of a hero for outcome resolution.
 *
 * ## Contract
 * - Power is derived from rank (ordinal+1), class bonus, and experience bonus.
 * - Null hero returns fallback power of 1 (minimum viable combatant).
 *
 * ## Preconditions
 * - None (null-safe).
 *
 * ## Postconditions
 * - Returns a positive integer >= 1.
 *
 * ## Determinism
 * - Pure function; same hero always produces same power.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 *
 * @param hero Hero to evaluate (null = fallback power).
 * @return Effective power value (>= 1).
 */
fun calculateHeroPower(hero: Hero?): Int {
    if (hero == null) return 1
    val rankPower = hero.rank.ordinal + 1
    val classPower = when (hero.klass) {
        HeroClass.WARRIOR -> 2
        HeroClass.MAGE -> 1
        HeroClass.HEAL -> 1
    }
    val experienceBonus = hero.historyCompleted / 10
    return rankPower + classPower + experienceBonus
}
