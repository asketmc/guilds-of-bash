package test

import core.*
import core.primitives.*
import core.rng.Rng
import core.state.*
import kotlin.test.*

/**
 * P1 CRITICAL: Trophy pipeline tests.
 * Tests trophy generation on resolve, deposit on CloseReturn, and selling.
 */
class P1_TrophyPipelineTest {

    @Test
    fun `resolve creates return with nonnegative trophies`() {
        // GIVEN a posted+active contract that resolves
        val rng = Rng(100L)
        var state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Test",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
                    )
                ),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 1,
                        daysRemaining = 1,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            ),
            heroes = HeroState(
                roster = listOf(
                    Hero(
                        id = HeroId(1),
                        name = "Hero #1",
                        rank = Rank.F,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(greed = 50, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        // WHEN AdvanceDay causes resolution (daysRemaining goes from 1 to 0)
        val cmd = AdvanceDay(cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN ContractResolved emitted
        val resolved = result.events.filterIsInstance<ContractResolved>()
        assertEquals(1, resolved.size)
        val resolvedEvent = resolved[0]

        // Trophies must be non-negative
        assertTrue(resolvedEvent.trophiesCount >= 0, "trophiesCount must be >= 0")

        // For SUCCESS, trophies should be in [1..3]
        if (resolvedEvent.outcome == Outcome.SUCCESS) {
            assertTrue(resolvedEvent.trophiesCount in 1..3, "SUCCESS should give 1-3 trophies")
        } else {
            assertEquals(0, resolvedEvent.trophiesCount, "FAILURE should give 0 trophies")
        }

        // Quality must be valid enum value
        assertTrue(resolvedEvent.quality in Quality.entries, "quality must be valid")

        // ReturnPacket must exist with matching data
        val returns = state.contracts.returns
        assertEquals(1, returns.size)
        val returnPacket = returns[0]
        assertEquals(resolvedEvent.trophiesCount, returnPacket.trophiesCount)
        assertEquals(resolvedEvent.quality, returnPacket.trophiesQuality)
        assertTrue(returnPacket.trophiesCount >= 0, "ReturnPacket trophiesCount must be >= 0")
    }

    @Test
    fun `close deposits trophies to stock`() {
        // GIVEN return trophiesCount = 2, stock=0
        val rng = Rng(100L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 0,
                trophiesStock = 0
            ),
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Test",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
                    )
                ),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 1,
                        daysRemaining = 0,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.RETURN_READY
                    )
                ),
                returns = listOf(
                    ReturnPacket(
                        boardContractId = ContractId(1),
                        heroIds = listOf(HeroId(1)),
                        activeContractId = ActiveContractId(1),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 2,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
                    )
                )
            ),
            heroes = HeroState(
                roster = listOf(
                    Hero(
                        id = HeroId(1),
                        name = "Hero #1",
                        rank = Rank.F,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(greed = 50, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        assertEquals(0, state.economy.trophiesStock, "Initial stock should be 0")

        // WHEN CloseReturn
        val cmd = CloseReturn(activeContractId = 1L, cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN stock=2, return removed
        assertEquals(2, state.economy.trophiesStock, "Stock should be 2 after close")

        val remainingReturns = state.contracts.returns.filter { it.activeContractId.value == 1 }
        assertEquals(0, remainingReturns.size, "Return should be removed")

        val closed = result.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, closed.size)
    }

    @Test
    fun `sell all after close increases money`() {
        // GIVEN after CloseReturn stock=3, money=100
        val rng = Rng(100L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 0,
                trophiesStock = 3
            )
        )

        // WHEN SellTrophies(0) - sell all
        val cmd = SellTrophies(amount = 0, cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN stock=0, money=103, TrophySold(amount=3, moneyGained=3)
        assertEquals(0, state.economy.trophiesStock, "Stock should be 0 after sell")
        assertEquals(103, state.economy.moneyCopper, "Money should increase by 3")

        val sold = result.events.filterIsInstance<TrophySold>()
        assertEquals(1, sold.size)
        assertEquals(3, sold[0].amount)
        assertEquals(3, sold[0].moneyGained)
    }

    @Test
    fun `multiple contracts generate independent trophy counts`() {
        // Test that different contracts can generate different trophy amounts
        val rng = Rng(123L)
        var state = initialState(42u)

        // Create multiple contracts that will resolve
        state = state.copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Contract 1",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
                    ),
                    BoardContract(
                        id = ContractId(2),
                        postedDay = 0,
                        title = "Contract 2",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
                    )
                ),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 1,
                        daysRemaining = 1,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    ),
                    ActiveContract(
                        id = ActiveContractId(2),
                        boardContractId = ContractId(2),
                        takenDay = 1,
                        daysRemaining = 1,
                        heroIds = listOf(HeroId(2)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            ),
            heroes = HeroState(
                roster = listOf(
                    Hero(
                        id = HeroId(1),
                        name = "Hero #1",
                        rank = Rank.F,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(greed = 50, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    ),
                    Hero(
                        id = HeroId(2),
                        name = "Hero #2",
                        rank = Rank.F,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(greed = 50, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        // Resolve both
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result.state

        val resolved = result.events.filterIsInstance<ContractResolved>()
        assertEquals(2, resolved.size)

        // Both should have valid trophy counts
        resolved.forEach { event ->
            assertTrue(event.trophiesCount >= 0)
            if (event.outcome == Outcome.SUCCESS) {
                assertTrue(event.trophiesCount in 1..3)
            }
        }

        // Should have 2 returns
        assertEquals(2, state.contracts.returns.size)
    }

    @Test
    fun `end to end trophy flow from resolve to sell`() {
        // Full pipeline: resolve -> close -> sell
        val rng = Rng(200L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 50,
                reservedCopper = 0,
                trophiesStock = 0
            ),
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Test",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
                    )
                ),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 1,
                        daysRemaining = 1,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            ),
            heroes = HeroState(
                roster = listOf(
                    Hero(
                        id = HeroId(1),
                        name = "Hero #1",
                        rank = Rank.F,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(greed = 50, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        val initialMoney = state.economy.moneyCopper

        // Step 1: Resolve
        val result1 = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result1.state

        val resolved = result1.events.filterIsInstance<ContractResolved>()
        assertEquals(1, resolved.size)
        val trophiesGenerated = resolved[0].trophiesCount
        assertTrue(trophiesGenerated > 0, "Should generate trophies for SUCCESS")

        // Trophies not yet in stock
        assertEquals(0, state.economy.trophiesStock)

        // Step 2: Close
        val result2 = step(state, CloseReturn(activeContractId = 1L, cmdId = 2L), rng)
        state = result2.state

        // Trophies now in stock
        assertEquals(trophiesGenerated, state.economy.trophiesStock)
        assertEquals(initialMoney, state.economy.moneyCopper, "Money unchanged after close")

        // Step 3: Sell
        val result3 = step(state, SellTrophies(amount = 0, cmdId = 3L), rng)
        state = result3.state

        // Stock cleared, money increased
        assertEquals(0, state.economy.trophiesStock)
        assertEquals(initialMoney + trophiesGenerated, state.economy.moneyCopper)

        val sold = result3.events.filterIsInstance<TrophySold>()
        assertEquals(1, sold.size)
        assertEquals(trophiesGenerated, sold[0].amount)
    }
}
