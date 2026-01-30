// FILE: core/src/main/kotlin/core/pipeline/ContractPickupModel.kt
package core.pipeline

import core.BalanceSettings
import core.evaluateContractForHero
import core.primitives.ActiveStatus
import core.primitives.ActiveContractId
import core.primitives.BoardStatus
import core.primitives.ContractId
import core.primitives.HeroId
import core.primitives.HeroStatus
import core.state.*

/**
 * Contract pickup decision model.
 *
 * ## Semantic Ownership
 * Answers: **Which contract should a hero pick?**
 *
 * ## Stability Gradient
 * STABLE: Pure decision logic with explicit rules.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 * - Iteration order is deterministic (sorted by ID).
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state directly.
 */
object ContractPickupModel {

    /**
     * Computes contract pickup decisions for arriving heroes.
     *
     * @param arrivingHeroIds Heroes arriving today (sorted by ID).
     * @param roster Current hero roster.
     * @param board Current board contracts.
     * @param currentDay Current day index.
     * @param startingActiveContractId Starting ID for new active contracts.
     * @return [PickupResult] with all pickup decisions.
     */
    fun computePickups(
        arrivingHeroIds: List<HeroId>,
        roster: List<Hero>,
        board: List<BoardContract>,
        currentDay: Int,
        startingActiveContractId: Int
    ): PickupResult {
        val sortedHeroIds = arrivingHeroIds.sortedBy { it.value }
        var nextActiveContractId = startingActiveContractId

        val rosterIndexById = HashMap<Int, Int>(roster.size * 2)
        for (i in roster.indices) rosterIndexById[roster[i].id.value] = i

        val mutableBoard = board.toMutableList()
        val mutableRoster = roster.toMutableList()
        val newActives = ArrayList<ActiveContract>()
        val pickupDecisions = ArrayList<PickupDecision>()

        // Track open contract indices
        val openIdx = IntArray(board.size)
        var openCount = 0
        for (i in board.indices) {
            if (board[i].status == BoardStatus.OPEN) openIdx[openCount++] = i
        }

        for (heroId in sortedHeroIds) {
            val heroIndex = rosterIndexById[heroId.value] ?: continue
            val hero = mutableRoster[heroIndex]

            var bestBoardIndex = -1
            var bestScore = Int.MIN_VALUE
            var bestBoard: BoardContract? = null

            for (k in 0 until openCount) {
                val bi = openIdx[k]
                val b = mutableBoard[bi]
                if (b.status != BoardStatus.OPEN) continue
                val score = evaluateContractForHero(hero, b, b.baseDifficulty).score
                if (score > bestScore) {
                    bestScore = score
                    bestBoardIndex = bi
                    bestBoard = b
                }
            }

            if (bestBoardIndex < 0) {
                pickupDecisions.add(
                    PickupDecision(
                        heroId = heroId,
                        decision = PickupDecisionType.NO_CONTRACT,
                        boardContractId = null,
                        activeContractId = null,
                        declineReason = null
                    )
                )
                continue
            }

            val chosen = bestBoard!!

            if (bestScore < 0) {
                val reason = if (bestScore < BalanceSettings.DECLINE_HARD_THRESHOLD) "unprofitable" else "too_risky"
                pickupDecisions.add(
                    PickupDecision(
                        heroId = heroId,
                        decision = PickupDecisionType.DECLINED,
                        boardContractId = chosen.id,
                        activeContractId = null,
                        declineReason = reason
                    )
                )
                continue
            }

            val activeId = nextActiveContractId++
            newActives.add(
                ActiveContract(
                    id = ActiveContractId(activeId),
                    boardContractId = chosen.id,
                    takenDay = currentDay,
                    daysRemaining = BalanceSettings.DAYS_REMAINING_INIT,
                    heroIds = listOf(heroId),
                    status = ActiveStatus.WIP
                )
            )

            mutableBoard[bestBoardIndex] = chosen.copy(status = BoardStatus.LOCKED)
            mutableRoster[heroIndex] = hero.copy(status = HeroStatus.ON_MISSION)

            pickupDecisions.add(
                PickupDecision(
                    heroId = heroId,
                    decision = PickupDecisionType.ACCEPTED,
                    boardContractId = chosen.id,
                    activeContractId = ActiveContractId(activeId),
                    declineReason = null
                )
            )
        }

        return PickupResult(
            decisions = pickupDecisions,
            newActives = newActives,
            updatedBoard = mutableBoard,
            updatedRoster = mutableRoster,
            nextActiveContractId = nextActiveContractId
        )
    }
}

/**
 * Type of pickup decision.
 */
enum class PickupDecisionType {
    ACCEPTED,
    DECLINED,
    NO_CONTRACT
}

/**
 * Single hero's pickup decision.
 */
data class PickupDecision(
    val heroId: HeroId,
    val decision: PickupDecisionType,
    val boardContractId: ContractId?,
    val activeContractId: ActiveContractId?,
    val declineReason: String?
)

/**
 * Result of all pickup decisions for the day.
 */
data class PickupResult(
    val decisions: List<PickupDecision>,
    val newActives: List<ActiveContract>,
    val updatedBoard: List<BoardContract>,
    val updatedRoster: List<Hero>,
    val nextActiveContractId: Int
)
