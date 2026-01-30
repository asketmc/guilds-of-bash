// FILE: core/src/main/kotlin/core/pipeline/HeroSupplyModel.kt
package core.pipeline

import core.primitives.HeroClass
import core.primitives.HeroId
import core.primitives.HeroStatus
import core.primitives.Rank
import core.rng.Rng
import core.state.Hero
import core.state.Traits

/**
 * Hero supply model.
 *
 * ## Semantic Ownership
 * Answers: **What heroes should arrive?**
 *
 * ## Stability Gradient
 * STABLE: Pure generation logic with explicit rules.
 *
 * ## Determinism
 * - RNG usage is explicit and ordered.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state directly.
 */
object HeroSupplyModel {

    /**
     * Generates arriving heroes for the day.
     *
     * @param count Number of heroes to generate.
     * @param startingHeroId Starting ID for new heroes.
     * @param rng Deterministic RNG.
     * @return [HeroArrivalResult] with generated heroes and next ID.
     */
    fun generateArrivals(
        count: Int,
        startingHeroId: Int,
        rng: Rng
    ): HeroArrivalResult {
        val heroes = ArrayList<Hero>(count)
        val heroIds = IntArray(count)
        var nextId = startingHeroId

        for (i in 0 until count) {
            val heroId = nextId++
            heroIds[i] = heroId
            val heroName = core.flavour.HeroNames.POOL[rng.nextInt(core.flavour.HeroNames.POOL.size)]

            heroes.add(
                Hero(
                    id = HeroId(heroId),
                    name = heroName,
                    rank = Rank.F,
                    klass = HeroClass.WARRIOR,
                    traits = Traits(greed = 50, honesty = 50, courage = 50),
                    status = HeroStatus.AVAILABLE,
                    historyCompleted = 0
                )
            )
        }

        val arrivalIds = heroes.map { it.id }

        return HeroArrivalResult(
            heroes = heroes,
            heroIds = heroIds,
            arrivalIds = arrivalIds,
            nextHeroId = nextId
        )
    }
}

/**
 * Result of hero arrival generation.
 */
data class HeroArrivalResult(
    val heroes: List<Hero>,
    val heroIds: IntArray,
    val arrivalIds: List<HeroId>,
    val nextHeroId: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is HeroArrivalResult) return false
        return heroes == other.heroes && heroIds.contentEquals(other.heroIds) && arrivalIds == other.arrivalIds && nextHeroId == other.nextHeroId
    }

    override fun hashCode(): Int {
        var result = heroes.hashCode()
        result = 31 * result + heroIds.contentHashCode()
        result = 31 * result + arrivalIds.hashCode()
        result = 31 * result + nextHeroId
        return result
    }
}
