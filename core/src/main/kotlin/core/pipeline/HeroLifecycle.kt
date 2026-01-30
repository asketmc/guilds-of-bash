// FILE: core/src/main/kotlin/core/pipeline/HeroLifecycle.kt
package core.pipeline

import core.primitives.HeroId
import core.primitives.HeroStatus
import core.primitives.Outcome
import core.state.Hero

/**
 * Hero lifecycle decision model.
 *
 * ## Semantic Ownership
 * Answers exactly one question: **Why hero became AVAILABLE / DEAD?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic with explicit rules.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT modify guild rank.
 * - Must NOT touch economy.
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object HeroLifecycle {

    /**
     * Computes hero state changes after contract resolution.
     *
     * @param heroIds Heroes involved in the contract.
     * @param outcome The resolved outcome.
     * @param roster Current hero roster.
     * @param arrivalsToday Current day's arrivals.
     * @param requiresPlayerClose Whether the return requires manual close (affects hero status).
     * @return [HeroLifecycleResult] containing roster changes.
     */
    fun computePostResolution(
        heroIds: List<HeroId>,
        outcome: Outcome,
        roster: List<Hero>,
        arrivalsToday: List<HeroId>,
        requiresPlayerClose: Boolean = true
    ): HeroLifecycleResult {
        val heroIdSet = heroIds.map { it.value }.toSet()

        return when (outcome) {
            Outcome.DEATH -> {
                val remainingRoster = roster.filter { !heroIdSet.contains(it.id.value) }
                val remainingArrivals = arrivalsToday.filter { !heroIdSet.contains(it.value) }
                val diedHeroIds = heroIdSet.toList()

                HeroLifecycleResult(
                    updatedRoster = remainingRoster,
                    updatedArrivalsToday = remainingArrivals,
                    diedHeroIds = diedHeroIds,
                    heroRemoved = true
                )
            }
            else -> {
                // For auto-close (requiresPlayerClose=false), hero becomes AVAILABLE immediately.
                // For manual-close (requiresPlayerClose=true), hero stays ON_MISSION until CloseReturn.
                val newStatus = if (requiresPlayerClose) HeroStatus.ON_MISSION else HeroStatus.AVAILABLE
                val updatedRoster = roster.map { hero ->
                    if (heroIdSet.contains(hero.id.value)) {
                        hero.copy(
                            status = newStatus,
                            historyCompleted = hero.historyCompleted + 1
                        )
                    } else hero
                }

                HeroLifecycleResult(
                    updatedRoster = updatedRoster,
                    updatedArrivalsToday = arrivalsToday,
                    diedHeroIds = emptyList(),
                    heroRemoved = false
                )
            }
        }
    }

    /**
     * Marks heroes as ON_MISSION when they accept a contract.
     *
     * @param heroId Hero accepting the contract.
     * @param roster Current hero roster.
     * @return Updated roster with hero marked as ON_MISSION.
     */
    fun markOnMission(heroId: HeroId, roster: List<Hero>): List<Hero> {
        return roster.map { hero ->
            if (hero.id == heroId) {
                hero.copy(status = HeroStatus.ON_MISSION)
            } else hero
        }
    }

    /**
     * Updates hero status to AVAILABLE for manual close (non-death outcomes).
     */
    fun computeManualCloseUpdate(
        heroIds: List<HeroId>,
        roster: List<Hero>
    ): List<Hero> {
        val heroIdSet = heroIds.map { it.value }.toSet()
        return roster.map { hero ->
            if (heroIdSet.contains(hero.id.value)) {
                hero.copy(
                    status = HeroStatus.AVAILABLE,
                    historyCompleted = hero.historyCompleted + 1
                )
            } else hero
        }
    }
}

/**
 * Result of hero lifecycle computation.
 */
data class HeroLifecycleResult(
    /** Updated roster after processing. */
    val updatedRoster: List<Hero>,
    /** Updated arrivals today (may change on death). */
    val updatedArrivalsToday: List<HeroId>,
    /** IDs of heroes who died. */
    val diedHeroIds: List<Int>,
    /** Whether any hero was removed from roster. */
    val heroRemoved: Boolean
)
