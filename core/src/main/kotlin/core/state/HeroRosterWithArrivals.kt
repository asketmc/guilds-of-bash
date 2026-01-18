package core.state

import core.primitives.*

data class HeroState(
    val roster: List<Hero>,
    val arrivalsToday: List<HeroId>
)

data class Hero(
    val id: HeroId,
    val name: String,
    val rank: Rank,
    val klass: HeroClass,
    val traits: Traits,
    val status: HeroStatus,
    val historyCompleted: Int
)

data class Traits(
    val greed: Int,
    val honesty: Int,
    val courage: Int
)
