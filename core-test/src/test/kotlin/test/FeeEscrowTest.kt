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
 *
 * Accounting model:
 * - PostContract: money += clientDeposit, reserved += clientDeposit (deposit is escrowed)
 * - CloseReturn SUCCESS: money -= fee, reserved -= clientDeposit (pay hero, release escrow)
 * - CloseReturn FAIL: reserved -= clientDeposit (release escrow, no payout)
 */
@P2
class FeeEscrowTest {

    @Test
    fun `posting contract with fee reserves client deposit`() {
        val rng = Rng(1L)
        val deposit = 5

        // State with an inbox draft that has a client deposit
        val state = stateWithEconomy(moneyCopper = 100, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = listOf(contractDraft(id = 1L, clientDeposit = deposit)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        val result = step(state, PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 2L), rng)
        val newState = result.state

        // Guild receives client deposit and locks it
        assertEquals(100 + deposit, newState.economy.moneyCopper, "Money should include client deposit")
        assertReservedCopper(newState, deposit, "Client deposit should be reserved")

        // ContractPosted event was emitted with correct fee and deposit
        val posted = result.events.filterIsInstance<ContractPosted>()
        assertEquals(1, posted.size)
        assertEquals(10, posted[0].fee)
        assertEquals(deposit, posted[0].clientDeposit)
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
    fun `close pays hero fee from guild treasury`() {
        // GIVEN: contract posted with deposit=10, fee=30
        // State after PostContract: money = 100 + 10 = 110, reserved = 10
        val rng = Rng(100L)
        val deposit = 10
        val fee = 30
        var state = stateWithEconomy(moneyCopper = 100 + deposit, reservedCopper = deposit, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = fee, status = BoardStatus.LOCKED, clientDeposit = deposit)),
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

        // THEN: money -= fee (110 - 30 = 80), reserved -= deposit (10 - 10 = 0)
        assertEquals(80, state.economy.moneyCopper)
        assertReservedCopper(state, 0)

        val closed = result.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, closed.size)
    }

    @Test
    fun `take does not pay out`() {
        // GIVEN posted board with deposit=10, fee=30
        val rng = Rng(100L)
        val deposit = 10
        val fee = 30
        var state = stateWithEconomy(moneyCopper = 100 + deposit, reservedCopper = deposit, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = fee, status = BoardStatus.OPEN, clientDeposit = deposit)),
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
            assertEquals(110, state.economy.moneyCopper)
            assertReservedCopper(state, deposit)
        } else {
            // If no one took it (non-deterministic), assert invariant holds anyway
            assertEquals(110, state.economy.moneyCopper)
            assertReservedCopper(state, deposit)
        }
    }

    @Test
    fun `close releases escrow on failure without payout`() {
        // GIVEN: contract posted with deposit=10, fee=10
        // State after PostContract: money = 100 + 10 = 110, reserved = 10
        val rng = Rng(2L)
        val deposit = 10
        val fee = 10
        var state = stateWithEconomy(moneyCopper = 100 + deposit, reservedCopper = deposit, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = fee, status = BoardStatus.LOCKED, clientDeposit = deposit)),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
                returns = listOf(
                    returnPacket(activeId = 1L, heroIds = listOf(1L), outcome = Outcome.FAIL, trophiesCount = 0)
                )
            )
        )

        val result = step(state, CloseReturn(activeContractId = 1L, cmdId = 1L), rng)
        state = result.state

        // FAIL: no payout, escrow released
        // money unchanged at 110, reserved = 0
        assertEquals(110, state.economy.moneyCopper, "Money should be unchanged after failure")
        assertReservedCopper(state, 0, "Reserved should be released")

        val closed = result.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, closed.size)
    }

    @Test
    fun `post accepts when clientDeposit covers entire fee with zero money`() {
        // GIVEN money=0 reserved=0 available=0, inbox draft with clientDeposit=10
        val rng = Rng(100L)
        val deposit = 10
        val state = stateWithEconomy(moneyCopper = 0, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = listOf(contractDraft(id = 1L, clientDeposit = deposit)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN PostContract fee=10 (fully covered by clientDeposit)
        val cmd = PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 5L)
        val result = step(state, cmd, rng)

        // THEN accepted (no CommandRejected), contract posted
        val rejections = result.events.filterIsInstance<CommandRejected>()
        assertTrue(rejections.isEmpty(), "Should not be rejected: $rejections")

        val posted = result.events.filterIsInstance<ContractPosted>()
        assertEquals(1, posted.size, "Contract should be posted")
        assertEquals(10, posted[0].fee)
        assertEquals(deposit, posted[0].clientDeposit)

        // Guild receives deposit and reserves it
        assertEquals(deposit, result.state.economy.moneyCopper, "Money should include client deposit")
        assertReservedCopper(result.state, deposit, "Client deposit should be reserved")
    }

    @Test
    fun `post accepts when clientDeposit partially covers fee`() {
        // GIVEN money=5 reserved=0 available=5, inbox draft with clientDeposit=5
        val rng = Rng(100L)
        val deposit = 5
        val state = stateWithEconomy(moneyCopper = 5, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = listOf(contractDraft(id = 1L, clientDeposit = deposit)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN PostContract fee=10 (5 from deposit, need 5 from guild -> have 5 available)
        val cmd = PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 5L)
        val result = step(state, cmd, rng)

        // THEN accepted
        val rejections = result.events.filterIsInstance<CommandRejected>()
        assertTrue(rejections.isEmpty(), "Should not be rejected: $rejections")

        val posted = result.events.filterIsInstance<ContractPosted>()
        assertEquals(1, posted.size, "Contract should be posted")
    }

    @Test
    fun `post rejects when clientDeposit insufficient and no available funds`() {
        // GIVEN money=0 reserved=0 available=0, inbox draft with clientDeposit=5
        val rng = Rng(100L)
        val deposit = 5
        val state = stateWithEconomy(moneyCopper = 0, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = listOf(contractDraft(id = 1L, clientDeposit = deposit)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN PostContract fee=10 (5 from deposit, need 5 from guild -> have 0 available)
        val cmd = PostContract(inboxId = 1L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 5L)
        val result = step(state, cmd, rng)

        // THEN rejected with INVALID_STATE
        assertSingleRejection(result.events, RejectReason.INVALID_STATE)
    }
}
