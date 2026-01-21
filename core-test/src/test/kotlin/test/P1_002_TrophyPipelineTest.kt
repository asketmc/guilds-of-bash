package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.*
import core.rng.Rng
import core.state.*
import kotlin.test.*

/**
 * P1 CRITICAL: Trophy pipeline tests.
 * Tests trophy generation on resolve, deposit on CloseReturn, and selling.
 */
class P1_002_TrophyPipelineTest {

    @Test
    fun `resolve creates return with nonnegative trophies`() {
        // GIVEN: a posted+active contract that will resolve after AdvanceDay
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
                        baseDifficulty = 1,
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
                        traits = Traits(greed = 0, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        // WHEN: AdvanceDay causes resolution (daysRemaining goes from 1 to 0)
        val cmd = AdvanceDay(cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN: ContractResolved emitted and return created with valid trophy counts and quality
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
        // GIVEN: return trophiesCount = 2 and initial stock=0
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
                        baseDifficulty = 1,
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
                        requiresPlayerClose = true,
                        suspectedTheft = false
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
                        traits = Traits(greed = 0, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        val initialMoney = state.economy.moneyCopper

        // WHEN: CloseReturn
        val cmd = CloseReturn(activeContractId = 1L, cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN: stock=2, return removed
        assertEquals(2, state.economy.trophiesStock, "Stock should be 2 after close")

        val remainingReturns = state.contracts.returns.filter { it.activeContractId.value == 1 }
        assertEquals(0, remainingReturns.size, "Return should be removed")

        val closed = result.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, closed.size)
    }

    @Test
    fun `sell all after close increases money`() {
        // GIVEN: after CloseReturn stock=3 and money=100
        val rng = Rng(100L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 0,
                trophiesStock = 3
            )
        )

        // WHEN: SellTrophies(0) - sell all
        val cmd = SellTrophies(amount = 0, cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN: stock=0, money=103, TrophySold(amount=3, moneyGained=3)
        assertEquals(0, state.economy.trophiesStock, "Stock should be 0 after sell")
        assertEquals(103, state.economy.moneyCopper, "Money should increase by 3")

        val sold = result.events.filterIsInstance<TrophySold>()
        assertEquals(1, sold.size)
        assertEquals(3, sold[0].amount)
        assertEquals(3, sold[0].moneyGained)
    }

    @Test
    fun `multiple contracts generate independent trophy counts`() {
        // GIVEN: multiple active contracts that will resolve with deterministic RNG
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
                        baseDifficulty = 1,
                        status = BoardStatus.LOCKED
                    ),
                    BoardContract(
                        id = ContractId(2),
                        postedDay = 0,
                        title = "Contract 2",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
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
                        traits = Traits(greed = 0, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    ),
                    Hero(
                        id = HeroId(2),
                        name = "Hero #2",
                        rank = Rank.F,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(greed = 0, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        // WHEN: Resolve both via AdvanceDay
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result.state

        // THEN: two ContractResolved events and two returns created with valid trophy counts
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
        // GIVEN: Full pipeline with initial money and a WIP contract that will resolve
        val rng = Rng(200L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 50,
                reservedCopper = 50,
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
                        fee = 50,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 5,
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
                        rank = Rank.D,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(greed = 0, honesty = 50, courage = 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 10
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
        val outcome = resolved[0].outcome
        val trophiesGenerated = resolved[0].trophiesCount
        assertTrue(trophiesGenerated >= 0, "Should generate non-negative trophies")

        val requiresClose = state.contracts.returns.firstOrNull()?.requiresPlayerClose ?: false

        if (requiresClose) {
            // PARTIAL outcome - requires manual close
            assertEquals(0, state.economy.trophiesStock)

            // Step 2: Close
            val result2 = step(state, CloseReturn(activeContractId = 1L, cmdId = 2L), rng)
            state = result2.state

            // Trophies now in stock (GUILD salvage)
            assertEquals(trophiesGenerated, state.economy.trophiesStock)
            assertEquals(0, state.economy.reservedCopper, "Escrow should be released after close")
            assertEquals(initialMoney - 50, state.economy.moneyCopper, "Money decreased by fee after close")
        } else {
            // SUCCESS/FAIL outcome - auto-processed
            val expectedStock = if (outcome == Outcome.SUCCESS) trophiesGenerated else 0
            val expectedMoney = if (outcome == Outcome.SUCCESS) (initialMoney - 50) else initialMoney

            assertEquals(expectedStock, state.economy.trophiesStock, "Auto-processed outcome should apply trophy accounting")
            assertEquals(0, state.economy.reservedCopper, "Escrow should be released on auto-close")
            assertEquals(expectedMoney, state.economy.moneyCopper, "Auto-close should settle fee based on outcome")
        }

        // Step 3: Sell (only if we have trophies)
        if (state.economy.trophiesStock > 0) {
            val trophiesBeforeSell = state.economy.trophiesStock
            val moneyBeforeSell = state.economy.moneyCopper

            val result3 = step(state, SellTrophies(amount = 0, cmdId = 3L), rng)
            state = result3.state

            // Stock cleared, money increased
            assertEquals(0, state.economy.trophiesStock)
            assertEquals(moneyBeforeSell + trophiesBeforeSell, state.economy.moneyCopper)

            val sold = result3.events.filterIsInstance<TrophySold>()
            assertEquals(1, sold.size)
            assertEquals(trophiesBeforeSell, sold[0].amount)
        }
    }
}
