package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.primitives.Quality
import core.primitives.Rank
import test.helpers.advanceDay
import test.helpers.assertEndToEndTrophyFlow
import test.helpers.assertResolvedCount
import test.helpers.assertResolvedTrophiesValid
import test.helpers.assertSellAllApplied
import test.helpers.assertSingleResolvedCreatesSingleReturn
import test.helpers.assertTrophiesDepositedAndReturnRemoved
import test.helpers.closeReturn
import test.helpers.sellAllTrophies
import test.helpers.trophyCloseFixture
import test.helpers.trophyE2EFixture
import test.helpers.trophyResolveFixture
import test.helpers.trophySpec
import test.helpers.trophyStockFixture
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * P1 CRITICAL: Trophy pipeline tests.
 * Tests trophy generation on resolve, deposit on CloseReturn, and selling.
 */
@P1
class TrophyPipelineTest {

    @Test
    fun `resolve creates return with nonnegative trophies`() {
        val fx = trophyResolveFixture(
            rngSeed = 100L,
            specs = listOf(trophySpec(contractId = 1L, heroId = 1L, daysRemaining = 1))
        )

        val res = advanceDay(fx.initial, cmdId = 1L, rng = fx.rng)

        assertSingleResolvedCreatesSingleReturn(res.state, res.events)
    }

    @Test
    fun `close deposits trophies to stock`() {
        val fx = trophyCloseFixture(
            rngSeed = 100L,
            trophiesCount = 2,
            trophiesQuality = Quality.OK,
            initialMoney = 100,
            initialReserved = 0,
            initialStock = 0
        )

        val res = closeReturn(fx.initial, activeContractId = 1L, cmdId = 1L, rng = fx.rng)

        assertTrophiesDepositedAndReturnRemoved(
            before = fx.initial,
            after = res.state,
            activeContractId = 1L,
            expectedDeposited = 2
        )
        assertEquals(1, res.events.filterIsInstance<core.ReturnClosed>().size)
    }

    @Test
    fun `sell all after close increases money`() {
        val fx = trophyStockFixture(
            rngSeed = 100L,
            moneyCopper = 100,
            reservedCopper = 0,
            trophiesStock = 3
        )

        val res = sellAllTrophies(fx.initial, cmdId = 1L, rng = fx.rng)

        assertSellAllApplied(before = fx.initial, after = res.state, events = res.events)
    }

    @Test
    fun `multiple contracts generate independent trophy counts`() {
        val fx = trophyResolveFixture(
            seed = 42u,
            rngSeed = 123L,
            specs = listOf(
                trophySpec(contractId = 1L, heroId = 1L, daysRemaining = 1),
                trophySpec(contractId = 2L, heroId = 2L, daysRemaining = 1)
            )
        )

        val res = advanceDay(fx.initial, cmdId = 1L, rng = fx.rng)

        val resolved = assertResolvedCount(res.events, expected = 2)
        resolved.forEach { assertResolvedTrophiesValid(it) }

        assertEquals(2, res.state.contracts.returns.size)
    }

    @Test
    fun `end to end trophy flow from resolve to sell`() {
        val fx = trophyE2EFixture(
            seed = 42u,
            rngSeed = 200L,
            initialMoney = 50,
            initialReserved = 50,
            initialStock = 0,
            contractFee = 50,
            baseDifficulty = 5,
            heroRank = Rank.D,
            heroHistoryCompleted = 10
        )

        assertEndToEndTrophyFlow(fx)
    }
}
