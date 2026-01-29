package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.*
import core.state.*
import core.rng.Rng
import test.helpers.*
import kotlin.test.*

/**
 * P2: Fee escrow tests.
 * Important feature-level tests for fee handling.
 */
@P2
class FeeEscrowTest {

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
        assertReservedCopper(state, 10, "Fee should be reserved")

        // Also ensure ContractPosted event was emitted with correct fee
        val posted = result.events.filterIsInstance<ContractPosted>()
        assertEquals(1, posted.size)
        assertEquals(10, posted[0].fee)
    }

    @Test
    fun `post rejects if fee exceeds available`() {
        // GIVEN money=100 reserved=90, available=10, inbox id=1
        val rng = Rng(100L)
        val state = stateWithEconomy(moneyCopper = 100, reservedCopper = 90, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = listOf(contractDraft(id = 1L)),
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
        assertReservedCopper(result.state, 90)

        assertSingleRejection(result.events, RejectReason.INVALID_STATE)
    }

    @Test
    fun `close pays out from escrow`() {
        // GIVEN posted board with fee=30, taken to active, resolved -> return exists (success)
        val rng = Rng(100L)
        var state = stateWithEconomy(moneyCopper = 100, reservedCopper = 30, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = 30, status = BoardStatus.LOCKED)),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
                returns = listOf(
                    returnPacket(activeId = 1L, heroIds = listOf(1L), outcome = Outcome.SUCCESS, trophiesCount = 0)
                )
            ),
            heroes = HeroState(
                roster = listOf(hero(id = 1L, status = HeroStatus.ON_MISSION)),
                arrivalsToday = emptyList()
            )
        )

        // WHEN CloseReturn(activeId=1)
        val cmd = CloseReturn(activeContractId = 1L, cmdId = 6L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN money decreases by 30 and reserved decreases by 30, ReturnClosed emitted
        assertEquals(70, state.economy.moneyCopper)
        assertReservedCopper(state, 0)

        val closed = result.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, closed.size)
    }

    @Test
    fun `take does not pay out`() {
        // GIVEN posted board fee=30
        val rng = Rng(100L)
        var state = stateWithEconomy(moneyCopper = 100, reservedCopper = 30, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = 30, status = BoardStatus.OPEN)),
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
            assertReservedCopper(state, 30)
        } else {
            // If no one took it (non-deterministic), assert invariant holds anyway
            assertEquals(100, state.economy.moneyCopper)
            assertReservedCopper(state, 30)
        }
    }

    @Test
    fun `close returns reserved money on failure`() {
        val rng = Rng(2L)
        var state = stateWithEconomy(moneyCopper = 100, reservedCopper = 10, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = 10, status = BoardStatus.LOCKED)),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
                returns = listOf(
                    returnPacket(activeId = 1L, heroIds = listOf(1L), outcome = Outcome.FAIL, trophiesCount = 0)
                )
            )
        )

        val result = step(state, CloseReturn(activeContractId = 1L, cmdId = 1L), rng)
        state = result.state

        assertEquals(100, state.economy.moneyCopper, "Money should be restored after failure")
        assertReservedCopper(state, 0, "Reserved should be decreased")

        val closed = result.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, closed.size)
    }
}
