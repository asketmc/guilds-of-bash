package test

import core.*
import core.rng.Rng
import core.primitives.ContractId
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.primitives.BoardStatus
import core.state.initialState
import org.junit.jupiter.api.Test
import test.helpers.*
import kotlin.test.*

/**
 * P2: UpdateContractTerms Command Tests
 *
 * Validates R1 lifecycle command: UpdateContractTerms
 * Tests updating fee and salvage policy for both inbox and board contracts
 */
@P2
class UpdateContractTermsTest {


    @Test
    fun `UpdateContractTerms changes salvage policy in inbox`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        // Create a draft
        val createCmd = CreateContract(
            title = "Test Contract",
            rank = Rank.F,
            difficulty = 30,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, events1) = step(state, createCmd, rng)
        val draftId = events1.filterIsInstance<ContractDraftCreated>().single().draftId

        // Update salvage policy
        val updateCmd = UpdateContractTerms(
            contractId = draftId.toLong(),
            newFee = null,
            newSalvage = SalvagePolicy.HERO,
            cmdId = 2L
        )
        val (state3, events2) = step(state2, updateCmd, rng)

        val event = requireSingleMainEvent<ContractTermsUpdated>(events2)
        assertEquals(draftId, event.contractId)
        assertEquals("inbox", event.location)
        assertEquals(SalvagePolicy.GUILD, event.oldSalvage)
        assertEquals(SalvagePolicy.HERO, event.newSalvage)

        // Verify state updated
        val draft = state3.inbox.find { it.id == ContractId(draftId) }
        assertNotNull(draft)
        assertEquals(SalvagePolicy.HERO, draft.salvage)
    }

    @Test
    fun `UpdateContractTerms changes fee offered in inbox`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        // Create a draft
        val createCmd = CreateContract(
            title = "Test Contract",
            rank = Rank.F,
            difficulty = 30,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, events1) = step(state, createCmd, rng)
        val draftId = events1.filterIsInstance<ContractDraftCreated>().single().draftId

        // Update fee
        val updateCmd = UpdateContractTerms(
            contractId = draftId.toLong(),
            newFee = 25,
            newSalvage = null,
            cmdId = 2L
        )
        val (state3, events2) = step(state2, updateCmd, rng)

        val event = requireSingleMainEvent<ContractTermsUpdated>(events2)
        assertEquals(draftId, event.contractId)
        assertEquals("inbox", event.location)
        assertEquals(0, event.oldFee)
        assertEquals(25, event.newFee)

        // Verify state updated
        val draft = state3.inbox.find { it.id == ContractId(draftId) }
        assertNotNull(draft)
        assertEquals(25, draft.feeOffered)
    }

    @Test
    fun `UpdateContractTerms updates board contract fee with escrow adjustment`() {
        var state = initialState(123u)
        state = state.copy(economy = state.economy.copy(moneyCopper = 1000))
        val rng = Rng(456L)

        // Post a contract to board
        val draftId = state.inbox.first().id
        val postCmd = PostContract(
            inboxId = draftId.toLong(),
            fee = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, _) = step(state, postCmd, rng)

        val contractOnBoard = state2.board.contracts.values.first()
        val oldReserved = state2.economy.reservedCopper

        // Increase fee from 50 to 75
        val updateCmd = UpdateContractTerms(
            contractId = contractOnBoard.id.toLong(),
            newFee = 75,
            newSalvage = null,
            cmdId = 2L
        )
        val (state3, events) = step(state2, updateCmd, rng)

        val event = requireSingleMainEvent<ContractTermsUpdated>(events)
        assertEquals(contractOnBoard.id.value, event.contractId)
        assertEquals("board", event.location)
        assertEquals(50, event.oldFee)
        assertEquals(75, event.newFee)

        // Reserved copper stays the same (only clientDeposit is escrowed, not fee)
        assertEquals(oldReserved, state3.economy.reservedCopper)

        // Contract fee should be updated
        val updatedContract = state3.board.contracts[contractOnBoard.id]
        assertNotNull(updatedContract)
        assertEquals(75, updatedContract.fee)
    }

    @Test
    fun `UpdateContractTerms decreases board contract fee without affecting escrow`() {
        var state = initialState(123u)
        state = state.copy(economy = state.economy.copy(moneyCopper = 1000))
        val rng = Rng(456L)

        // Post a contract
        val draftId = state.inbox.first().id
        val postCmd = PostContract(
            inboxId = draftId.toLong(),
            fee = 100,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, _) = step(state, postCmd, rng)

        val contractOnBoard = state2.board.contracts.values.first()
        val oldReserved = state2.economy.reservedCopper

        // Decrease fee from 100 to 60
        val updateCmd = UpdateContractTerms(
            contractId = contractOnBoard.id.toLong(),
            newFee = 60,
            newSalvage = null,
            cmdId = 2L
        )
        val (state3, events) = step(state2, updateCmd, rng)

        val event = requireSingleMainEvent<ContractTermsUpdated>(events)
        assertEquals(100, event.oldFee)
        assertEquals(60, event.newFee)

        // Reserved copper stays the same (only clientDeposit is escrowed, not fee)
        assertEquals(oldReserved, state3.economy.reservedCopper)
    }

    @Test
    fun `UpdateContractTerms updates board contract salvage policy`() {
        var state = initialState(123u)
        state = state.copy(economy = state.economy.copy(moneyCopper = 1000))
        val rng = Rng(456L)

        // Post a contract
        val draftId = state.inbox.first().id
        val postCmd = PostContract(
            inboxId = draftId.toLong(),
            fee = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, _) = step(state, postCmd, rng)

        val contractOnBoard = state2.board.contracts.values.first()

        // Update salvage policy
        val updateCmd = UpdateContractTerms(
            contractId = contractOnBoard.id.toLong(),
            newFee = null,
            newSalvage = SalvagePolicy.SPLIT,
            cmdId = 2L
        )
        val (state3, events) = step(state2, updateCmd, rng)

        val event = requireSingleMainEvent<ContractTermsUpdated>(events)
        assertEquals(SalvagePolicy.GUILD, event.oldSalvage)
        assertEquals(SalvagePolicy.SPLIT, event.newSalvage)

        // Verify state updated
        val updatedContract = state3.board.contracts[contractOnBoard.id]
        assertNotNull(updatedContract)
        assertEquals(SalvagePolicy.SPLIT, updatedContract.salvage)
    }

    @Test
    fun `UpdateContractTerms rejects non-existent contract`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val updateCmd = UpdateContractTerms(
            contractId = 99999L,
            newFee = 50,
            newSalvage = null,
            cmdId = 1L
        )
        val (stateAfter, events) = step(state, updateCmd, rng)

        // Prefer canonical enum-based rejection check instead of fragile text matching
        assertSingleRejection(events, RejectReason.NOT_FOUND, "Should reject with NOT_FOUND reason")
        assertEquals(state, stateAfter, "State should be unchanged after NOT_FOUND rejection")
    }

    @Test
    fun `UpdateContractTerms rejects taken contract`() {
        var state = initialState(123u)
        state = state.copy(economy = state.economy.copy(moneyCopper = 1000))
        val rng = Rng(456L)

        // Post and take a contract
        val draftId = state.inbox.first().id
        val postCmd = PostContract(
            inboxId = draftId.toLong(),
            fee = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, _) = step(state, postCmd, rng)
        val contractOnBoard = state2.board.contracts.values.first()

        // Deterministically simulate a TAKEN/LOCKED board contract by locking the posted entry
        val lockedState = state2.copy(
             contracts = state2.contracts.copy(
                 board = state2.board.map { bc -> if (bc.id == contractOnBoard.id) bc.copy(status = BoardStatus.LOCKED) else bc }
             )
         )

        // Try to update the locked/taken contract and expect rejection
        val updateCmd = UpdateContractTerms(
            contractId = contractOnBoard.id.toLong(),
            newFee = 100,
            newSalvage = null,
            cmdId = 3L
        )
        val (stateAfter, events) = step(lockedState, updateCmd, rng)
        assertSingleRejection(events, RejectReason.INVALID_STATE, "Should reject non-OPEN contract")
        assertEquals(lockedState, stateAfter, "State should be unchanged after INVALID_STATE rejection")
    }

    @Test
    fun `UpdateContractTerms rejects insufficient escrow for fee increase`() {
        var state = initialState(123u)
        state = state.copy(economy = state.economy.copy(moneyCopper = 100, reservedCopper = 50))
        val rng = Rng(456L)

        // Post a contract
        val draftId = state.inbox.first().id
        val postCmd = PostContract(
            inboxId = draftId.toLong(),
            fee = 30,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, _) = step(state, postCmd, rng)

        val contractOnBoard = state2.board.contracts.values.first()

        // Try to increase fee beyond available money
        val updateCmd = UpdateContractTerms(
            contractId = contractOnBoard.id.toLong(),
            newFee = 500,  // Would require 470 additional copper
            newSalvage = null,
            cmdId = 2L
        )
        val (stateAfter, events) = step(state2, updateCmd, rng)

        assertSingleRejection(events, RejectReason.INVALID_STATE, "Should reject insufficient funds")
        assertEquals(state2, stateAfter, "State should be unchanged after INVALID_STATE rejection")
    }

    @Test
    fun `UpdateContractTerms with both fee and salvage changes`() {
        var state = initialState(123u)
        state = state.copy(economy = state.economy.copy(moneyCopper = 1000))
        val rng = Rng(456L)

        // Post a contract
        val draftId = state.inbox.first().id
        val postCmd = PostContract(
            inboxId = draftId.toLong(),
            fee = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, _) = step(state, postCmd, rng)

        val contractOnBoard = state2.board.contracts.values.first()

        // Update both fee and salvage
        val updateCmd = UpdateContractTerms(
            contractId = contractOnBoard.id.toLong(),
            newFee = 80,
            newSalvage = SalvagePolicy.HERO,
            cmdId = 2L
        )
        val (state3, events) = step(state2, updateCmd, rng)

        val event = requireSingleMainEvent<ContractTermsUpdated>(events)
        assertEquals(50, event.oldFee)
        assertEquals(80, event.newFee)
        assertEquals(SalvagePolicy.GUILD, event.oldSalvage)
        assertEquals(SalvagePolicy.HERO, event.newSalvage)

        // Verify both changes applied
        val updatedContract = state3.board.contracts[contractOnBoard.id]
        assertNotNull(updatedContract)
        assertEquals(80, updatedContract.fee)
        assertEquals(SalvagePolicy.HERO, updatedContract.salvage)
    }
}
