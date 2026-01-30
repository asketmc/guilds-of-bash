package test

import core.*
import core.primitives.*
import core.rng.Rng
import core.state.*
import test.helpers.*
import kotlin.test.Test
import kotlin.test.assertEquals

@P1
class ClientDepositEscrowInvariantTest {

    @Test
    fun escrow_hold_on_post() {
        val rng = Rng(1L)

        // GIVEN: an inbox draft where reward=clientDeposit
        val deposit = 7
        val fee = 10
        val state = stateWithEconomy(moneyCopper = 100, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = listOf(contractDraft(id = 1L, clientDeposit = deposit)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // WHEN: posting with HERO salvage
        val result = step(
            state,
            PostContract(inboxId = 1L, fee = fee, salvage = SalvagePolicy.HERO, cmdId = 1L),
            rng
        )

        // THEN: guild receives client deposit and locks it
        // money += deposit, reserved += deposit, available unchanged
        assertEquals(100 + deposit, result.state.economy.moneyCopper)
        assertReservedCopper(result.state, deposit)

        val posted = result.state.contracts.board.single()
        assertEquals(deposit, posted.clientDeposit)
    }

    @Test
    fun settlement_on_success_return_closed() {
        val rng = Rng(2L)

        val deposit = 7
        val fee = 10

        // GIVEN: contract is on locked board, taken, and a SUCCESS return exists requiring close
        // State represents post-PostContract: money includes deposit, reserved = deposit
        val state = stateWithEconomy(moneyCopper = 100 + deposit, reservedCopper = deposit, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = fee, status = BoardStatus.LOCKED, clientDeposit = deposit)),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
                returns = listOf(returnPacket(activeId = 1L, heroIds = listOf(1L), outcome = Outcome.SUCCESS, trophiesCount = 0))
            ),
            heroes = HeroState(
                roster = listOf(hero(id = 1L, status = HeroStatus.ON_MISSION)),
                arrivalsToday = emptyList()
            )
        )

        // WHEN: closing the return
        val result = step(state, CloseReturn(activeContractId = 1L, cmdId = 1L), rng)

        // THEN: guild pays hero fee, unlocks deposit
        // moneyDelta = -fee; reservedDelta = -deposit; availableDelta = -fee + deposit = deposit - fee
        val moneyDelta = result.state.economy.moneyCopper - state.economy.moneyCopper
        val reservedDelta = result.state.economy.reservedCopper - state.economy.reservedCopper
        val availableBefore = state.economy.moneyCopper - state.economy.reservedCopper
        val availableAfter = result.state.economy.moneyCopper - result.state.economy.reservedCopper
        val availableDelta = availableAfter - availableBefore

        assertEquals(-fee, moneyDelta)
        assertEquals(-deposit, reservedDelta)
        assertEquals(deposit - fee, availableDelta)
    }

    @Test
    fun no_double_settlement_idempotent_close() {
        val rng = Rng(3L)

        val deposit = 7
        val fee = 10

        // GIVEN: same pre-state as settlement_on_success_return_closed
        val state = stateWithEconomy(moneyCopper = 100 + deposit, reservedCopper = deposit, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = fee, status = BoardStatus.LOCKED, clientDeposit = deposit)),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
                returns = listOf(returnPacket(activeId = 1L, heroIds = listOf(1L), outcome = Outcome.SUCCESS, trophiesCount = 0))
            ),
            heroes = HeroState(
                roster = listOf(hero(id = 1L, status = HeroStatus.ON_MISSION)),
                arrivalsToday = emptyList()
            )
        )

        val first = step(state, CloseReturn(activeContractId = 1L, cmdId = 1L), rng)
        val second = step(first.state, CloseReturn(activeContractId = 1L, cmdId = 2L), rng)

        val moneyDelta2 = second.state.economy.moneyCopper - first.state.economy.moneyCopper
        val reservedDelta2 = second.state.economy.reservedCopper - first.state.economy.reservedCopper

        assertEquals(0, moneyDelta2)
        assertEquals(0, reservedDelta2)
    }

    @Test
    fun settlement_not_before_close() {
        val rng = Rng(4L)

        val deposit = 7
        val fee = 10

        // GIVEN: contract resolved but not ReturnClosed yet (return requires player close)
        val state = stateWithEconomy(moneyCopper = 100 + deposit, reservedCopper = deposit, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(boardContract(id = 1L, fee = fee, status = BoardStatus.LOCKED, clientDeposit = deposit)),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
                returns = listOf(returnPacket(activeId = 1L, heroIds = listOf(1L), outcome = Outcome.SUCCESS, trophiesCount = 0, requiresPlayerClose = true))
            ),
            heroes = HeroState(
                roster = listOf(hero(id = 1L, status = HeroStatus.ON_MISSION)),
                arrivalsToday = emptyList()
            )
        )

        // WHEN: applying a resolve-like command (AdvanceDay should not settle; settlement happens on CloseReturn)
        val resolved = step(state, AdvanceDay(cmdId = 1L), rng)

        // THEN: money/reserved unchanged
        val moneyDelta = resolved.state.economy.moneyCopper - state.economy.moneyCopper
        val reservedDelta = resolved.state.economy.reservedCopper - state.economy.reservedCopper
        assertEquals(0, moneyDelta)
        assertEquals(0, reservedDelta)
    }
}
