package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.*
import core.state.*
import core.rng.Rng
import kotlin.test.*

/**
 * P1 CRITICAL: Fee escrow tests.
 */
class P1_006_FeeEscrowTest {

    @Test
    fun `posting contract with fee reserves money`() {
        var state = initialState(42u)
        val rng = Rng(1L)

        // Advance day to get inbox
        state = step(state, AdvanceDay(cmdId = 1L), rng).state
        val inboxId = state.contracts.inbox.first().id.value.toLong()

        val result = step(state, PostContract(inboxId = inboxId, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L), rng)
        state = result.state

        assertEquals(100, state.economy.moneyCopper, "Money not deducted immediately")
        assertEquals(10, state.economy.reservedCopper, "Fee should be reserved")

        // Also ensure ContractPosted event was emitted with correct fee
        val posted = result.events.filterIsInstance<ContractPosted>()
        assertEquals(1, posted.size)
        assertEquals(10, posted[0].fee)
    }

    @Test
    fun `post rejects if fee exceeds available`() {
        // GIVEN money=100 reserved=90, available=10, inbox id=1
        val rng = Rng(100L)
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 90,
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
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "proof"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN PostContract fee=30 (exceeds available=10)
        val cmd = PostContract(inboxId = 1L, fee = 30, salvage = SalvagePolicy.GUILD, cmdId = 5L)
        val result = step(state, cmd, rng)

        // THEN state unchanged, events contain CommandRejected with reason INVALID_STATE
        assertEquals(100, result.state.economy.moneyCopper)
        assertEquals(90, result.state.economy.reservedCopper)

        val rejected = result.events.filterIsInstance<CommandRejected>()
        assertEquals(1, rejected.size)
        assertEquals(RejectReason.INVALID_STATE, rejected[0].reason)
        assertTrue(rejected[0].detail.contains("available", ignoreCase = true) || rejected[0].detail.contains("reserve", ignoreCase = true))
    }

    @Test
    fun `close pays out from escrow`() {
        // GIVEN posted board with fee=30, taken to active, resolved -> return exists (success)
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
                    // positional form: id, postedDay, title, rank, fee, salvage, baseDifficulty, status
                    BoardContract(
                        ContractId(1),
                        0,
                        "Test",
                        Rank.F,
                        30,
                        SalvagePolicy.GUILD,
                        1,
                        BoardStatus.LOCKED
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
                    // Use positional args to match core.state.ReturnPacket(activeContractId, boardContractId, heroIds, resolvedDay, outcome, trophiesCount, trophiesQuality, reasonTags, requiresPlayerClose, suspectedTheft)
                    ReturnPacket(
                        ActiveContractId(1),
                        ContractId(1),
                        listOf(HeroId(1)),
                        1,
                        Outcome.SUCCESS,
                        0,
                        Quality.OK,
                        emptyList(),
                        true,
                        false
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
        val cmd = CloseReturn(activeContractId = 1L, cmdId = 6L)
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
                        ContractId(1),
                        0,
                        "Test",
                        Rank.F,
                        30,
                        SalvagePolicy.GUILD,
                        1,
                        BoardStatus.OPEN
                    )
                ),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN AdvanceDay causes ContractTaken
        val cmd = AdvanceDay(cmdId = 7L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN money/reserved unchanged during take
        val taken = result.events.filterIsInstance<ContractTaken>()
        if (taken.isNotEmpty()) {
            assertEquals(100, state.economy.moneyCopper)
            assertEquals(30, state.economy.reservedCopper)
        } else {
            // If no one took it (non-deterministic), assert invariant holds anyway
            assertEquals(100, state.economy.moneyCopper)
            assertEquals(30, state.economy.reservedCopper)
        }
    }

    @Test
    fun `close returns reserved money on failure`() {
        val rng = Rng(2L)
        var state = initialState(42u).copy(
            economy = EconomyState(moneyCopper = 100, reservedCopper = 10, trophiesStock = 0),
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(ContractId(1), 0, "T", Rank.F, 10, SalvagePolicy.GUILD, 1, BoardStatus.LOCKED)
                ),
                active = listOf(ActiveContract(id = ActiveContractId(1), boardContractId = ContractId(1), takenDay = 1, daysRemaining = 0, heroIds = listOf(HeroId(1)), status = ActiveStatus.RETURN_READY)),
                returns = listOf(
                    // positional form again
                    ReturnPacket(
                        ActiveContractId(1),
                        ContractId(1),
                        listOf(HeroId(1)),
                        1,
                        Outcome.FAIL,
                        0,
                        Quality.OK,
                        emptyList(),
                        true,
                        false
                    )
                )
            )
        )

        val result = step(state, CloseReturn(activeContractId = 1L, cmdId = 1L), rng)
        state = result.state

        assertEquals(100, state.economy.moneyCopper, "Money should be restored after failure")
        assertEquals(0, state.economy.reservedCopper, "Reserved should be decreased")

        val closed = result.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, closed.size)
    }
}
