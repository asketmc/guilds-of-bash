// FILE: core-test/src/test/kotlin/test/P1_022_UpdateContractTermsTest.kt
package test

import core.*
import core.rng.Rng
import core.primitives.ContractId
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.state.initialState
import org.junit.jupiter.api.Test
import kotlin.test.*

/**
 * P1_022: UpdateContractTerms Command Tests
 *
 * Validates R1 lifecycle command: UpdateContractTerms
 * Tests updating fee and salvage policy for both inbox and board contracts
 */
class P1_022_UpdateContractTermsTest {

    @Test
    fun `UpdateContractTerms changes salvage policy in inbox`() {
        var state = initialState(123u)
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
        val draftId = (events1[0] as ContractDraftCreated).draftId

        // Update salvage policy
        val updateCmd = UpdateContractTerms(
            contractId = draftId.toLong(),
            newFee = null,
            newSalvage = SalvagePolicy.HERO,
            cmdId = 2L
        )
        val (state3, events2) = step(state2, updateCmd, rng)

        assertEquals(1, events2.size)
        val event = events2[0] as ContractTermsUpdated
        assertEquals(draftId, event.contractId)
        assertEquals("inbox", event.location)
        assertEquals(SalvagePolicy.GUILD, event.oldSalvage)
        assertEquals(SalvagePolicy.HERO, event.newSalvage)

        // Verify state updated
        val draft = state3.inbox.find { it.id == ContractId(draftId) }
        assertTrue(draft != null)
        assertEquals(SalvagePolicy.HERO, draft.salvage)
    }

    @Test
    fun `UpdateContractTerms changes fee offered in inbox`() {
        var state = initialState(123u)
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
        val draftId = (events1[0] as ContractDraftCreated).draftId

        // Update fee
        val updateCmd = UpdateContractTerms(
            contractId = draftId.toLong(),
            newFee = 25,
            newSalvage = null,
            cmdId = 2L
        )
        val (state3, events2) = step(state2, updateCmd, rng)

        assertEquals(1, events2.size)
        val event = events2[0] as ContractTermsUpdated
        assertEquals(draftId, event.contractId)
        assertEquals("inbox", event.location)
        assertEquals(0, event.oldFee)
        assertEquals(25, event.newFee)

        // Verify state updated
        val draft = state3.inbox.find { it.id == ContractId(draftId) }
        assertTrue(draft != null)
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

        assertEquals(1, events.size)
        val event = events[0] as ContractTermsUpdated
        assertEquals(contractOnBoard.id.value, event.contractId)
        assertEquals("board", event.location)
        assertEquals(50, event.oldFee)
        assertEquals(75, event.newFee)

        // Reserved copper should increase by 25
        assertEquals(oldReserved + 25, state3.economy.reservedCopper)

        // Contract fee should be updated
        val updatedContract = state3.board.contracts[contractOnBoard.id]
        assertTrue(updatedContract != null)
        assertEquals(75, updatedContract.fee)
    }

    @Test
    fun `UpdateContractTerms decreases board contract fee and releases escrow`() {
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

        assertEquals(1, events.size)
        val event = events[0] as ContractTermsUpdated
        assertEquals(100, event.oldFee)
        assertEquals(60, event.newFee)

        // Reserved copper should decrease by 40
        assertEquals(oldReserved - 40, state3.economy.reservedCopper)
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

        assertEquals(1, events.size)
        val event = events[0] as ContractTermsUpdated
        assertEquals(SalvagePolicy.GUILD, event.oldSalvage)
        assertEquals(SalvagePolicy.SPLIT, event.newSalvage)

        // Verify state updated
        val updatedContract = state3.board.contracts[contractOnBoard.id]
        assertTrue(updatedContract != null)
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
        val (_, events) = step(state, updateCmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as Rejected
        assertTrue(event.detail.contains("not found") || event.detail.contains("NOT_FOUND"),
            "Should reject with NOT_FOUND reason")
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

        val advanceCmd = AdvanceDay(cmdId = 2L)
        val (state3, _) = step(state2, advanceCmd, rng)

        // Find a taken contract
        val takenContract = state3.board.contracts.values.firstOrNull { it.status.name == "TAKEN" }
        if (takenContract != null) {
            // Try to update a taken contract
            val updateCmd = UpdateContractTerms(
                contractId = takenContract.id.toLong(),
                newFee = 100,
                newSalvage = null,
                cmdId = 3L
            )
            val (_, events) = step(state3, updateCmd, rng)

            assertEquals(1, events.size)
            val event = events[0] as Rejected
            assertTrue(event.detail.contains("OPEN") || event.detail.contains("status"),
                "Should reject non-OPEN contract")
        }
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
        val (_, events) = step(state2, updateCmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as Rejected
        assertTrue(event.detail.contains("money") || event.detail.contains("escrow"),
            "Should reject insufficient funds")
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

        assertEquals(1, events.size)
        val event = events[0] as ContractTermsUpdated
        assertEquals(50, event.oldFee)
        assertEquals(80, event.newFee)
        assertEquals(SalvagePolicy.GUILD, event.oldSalvage)
        assertEquals(SalvagePolicy.HERO, event.newSalvage)

        // Verify both changes applied
        val updatedContract = state3.board.contracts[contractOnBoard.id]
        assertTrue(updatedContract != null)
        assertEquals(80, updatedContract.fee)
        assertEquals(SalvagePolicy.HERO, updatedContract.salvage)
    }
}
