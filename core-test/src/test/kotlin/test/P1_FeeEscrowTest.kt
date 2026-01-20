package test

import core.*
import core.primitives.*
import core.rng.Rng
import core.state.*
import kotlin.test.*

/**
 * P1 CRITICAL: Fee escrow (reserve) and payout tests.
 * Tests that fees are escrowed on PostContract and paid out on CloseReturn.
 */
class P1_FeeEscrowTest {

    @Test
    fun `post reserves fee`() {
        // GIVEN money=100 reserved=0, inbox has id=1
        val rng = Rng(100L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 0,
                trophiesStock = 0
            ),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(1),
                        createdDay = 0,
                        title = "Test",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        baseDifficulty = 1,
                        proofHint = "proof"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN step(PostContract(inboxId=1, fee=30))
        val cmd = PostContract(inboxId = 1L, fee = 30, cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN reserved=30, money=100, available=70, ContractPosted emitted
        assertEquals(100, state.economy.moneyCopper)
        assertEquals(30, state.economy.reservedCopper)
        assertEquals(70, state.economy.moneyCopper - state.economy.reservedCopper)

        val posted = result.events.filterIsInstance<ContractPosted>()
        assertEquals(1, posted.size)
        assertEquals(30, posted[0].fee)
    }

    @Test
    fun `post rejects if fee exceeds available`() {
        // GIVEN money=100 reserved=80, available=20, inbox id=1
        val rng = Rng(100L)
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 80,
                trophiesStock = 0
            ),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(1),
                        createdDay = 0,
                        title = "Test",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        baseDifficulty = 1,
                        proofHint = "proof"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN PostContract fee=30 (exceeds available=20)
        val cmd = PostContract(inboxId = 1L, fee = 30, cmdId = 1L)
        val result = step(state, cmd, rng)

        // THEN state unchanged, events contain CommandRejected with reason INVALID_STATE
        assertEquals(100, result.state.economy.moneyCopper)
        assertEquals(80, result.state.economy.reservedCopper)

        val rejected = result.events.filterIsInstance<CommandRejected>()
        assertEquals(1, rejected.size)
        assertEquals(RejectReason.INVALID_STATE, rejected[0].reason)
        assertTrue(rejected[0].detail.contains("available", ignoreCase = true))
    }

    @Test
    fun `close pays out from escrow`() {
        // GIVEN posted board with fee=30, taken to active, resolved -> return exists
        val rng = Rng(100L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 30,
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
                        fee = 30,
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
                        trophiesCount = 0,
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

        // WHEN CloseReturn(activeId=1)
        val cmd = CloseReturn(activeContractId = 1L, cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN money decreases by 30 and reserved decreases by 30, ReturnClosed emitted
        assertEquals(70, state.economy.moneyCopper)
        assertEquals(0, state.economy.reservedCopper)

        val closed = result.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, closed.size)
    }

    @Test
    fun `take does not pay out`() {
        // GIVEN posted board fee=30
        val rng = Rng(100L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 30,
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
                        fee = 30,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.OPEN
                    )
                ),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN AdvanceDay causes ContractTaken
        val cmd = AdvanceDay(cmdId = 1L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN money/reserved unchanged during take
        // Note: money still 100, reserved still 30 (heroes arrived and took, but no payout yet)
        val taken = result.events.filterIsInstance<ContractTaken>()
        if (taken.isNotEmpty()) {
            assertEquals(100, state.economy.moneyCopper)
            assertEquals(30, state.economy.reservedCopper)
        }
    }
}
