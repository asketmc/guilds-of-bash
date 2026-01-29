package core.partial

import core.primitives.ActiveContractId
import core.primitives.BoardStatus
import core.primitives.ContractId
import core.primitives.HeroId
import core.primitives.Outcome
import core.primitives.Quality
import core.primitives.SalvagePolicy
import core.primitives.ActiveStatus
import core.state.Traits
import core.state.*

/**
 * Test-only factory for building deterministic states used in core.partial integration tests.
 *
 * Intentionally lives under core-test so production code is not affected.
 */
object TestStateFactory {

    /**
     * Builds a state that already contains:
     * - a board contract in LOCKED
     * - an active contract in RETURN_READY
     * - a ReturnPacket with outcome=PARTIAL and requiresPlayerClose=true
     *
     * The test encodes the intended new behavior: CloseReturn should apply a 50% money value rule.
     * Today the core doesn't support this hook yet, so the test should fail meaningfully.
     */
    fun stateWithPartialReturnReady(
        baseState: GameState,
        activeId: Long,
        normalMoneyValueCopper: Int
    ): GameState {
        // Encode the "normal money value" into board.fee so current CloseReturn logic has something deterministic to work with.
        // The future implementation can switch to a resolver input instead, but the test will remain stable.
        val board = BoardContract(
            id = ContractId(1),
            postedDay = baseState.meta.dayIndex,
            title = "Test",
            rank = core.primitives.Rank.F,
            fee = normalMoneyValueCopper,
            salvage = SalvagePolicy.GUILD,
            baseDifficulty = 1,
            status = BoardStatus.LOCKED,
            clientDeposit = 0
        )

        val heroId = HeroId(1)
        val active = ActiveContract(
            id = ActiveContractId(activeId.toInt()),
            boardContractId = board.id,
            takenDay = baseState.meta.dayIndex,
            daysRemaining = 0,
            heroIds = listOf(heroId),
            status = ActiveStatus.RETURN_READY
        )

        val ret = ReturnPacket(
            activeContractId = active.id,
            boardContractId = board.id,
            heroIds = listOf(heroId),
            resolvedDay = baseState.meta.dayIndex,
            outcome = Outcome.PARTIAL,
            trophiesCount = 1,
            trophiesQuality = Quality.OK,
            reasonTags = emptyList(),
            requiresPlayerClose = true,
            suspectedTheft = false
        )

        val hero = Hero(
            id = heroId,
            name = "Smith",
            rank = core.primitives.Rank.F,
            klass = core.primitives.HeroClass.WARRIOR,
            traits = Traits(greed = 50, honesty = 50, courage = 50),
            status = core.primitives.HeroStatus.AVAILABLE,
            historyCompleted = 0
        )

        return baseState.copy(
            contracts = baseState.contracts.copy(
                inbox = emptyList(),
                board = listOf(board),
                active = listOf(active),
                returns = listOf(ret)
            ),
            heroes = baseState.heroes.copy(roster = listOf(hero), arrivalsToday = emptyList()),
            economy = baseState.economy.copy(
                // Satisfy CloseReturn validation: moneyCopper and reservedCopper must cover the board fee.
                moneyCopper = normalMoneyValueCopper,
                reservedCopper = normalMoneyValueCopper,
                trophiesStock = baseState.economy.trophiesStock
            )
        )
    }
}
