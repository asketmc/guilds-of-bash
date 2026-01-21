package core

import core.state.Hero
import core.primitives.HeroClass

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
