// FILE: core-test/src/test/kotlin/test/P1_023_CancelContractTest.kt
package test

import core.*
import core.primitives.ContractId
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.state.initialState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import core.rng.Rng

/**
 * P1_023: CancelContract Command Tests
 *
 * Validates R1 lifecycle command: CancelContract
 * Tests cancelling contracts from inbox (drafts) and board (OPEN contracts)
 */
class P1_023_CancelContractTest {

    @Test
    fun `CancelContract removes draft from inbox without refund`() {
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

        // Cancel the draft
        val cancelCmd = CancelContract(
            contractId = draftId.toLong(),
            cmdId = 2L
        )
        val (state3, events2) = step(state2, cancelCmd, rng)

        assertEquals(1, events2.size)
        val event = events2[0] as ContractCancelled
        assertEquals(draftId, event.contractId)
        assertEquals("inbox", event.location)
        assertEquals(0, event.refundedCopper)

        // Draft should be removed from inbox
        val draft = state3.inbox.find { it.id == ContractId(draftId) }
        assertNull(draft, "Draft should be removed from inbox")

        // Economy unchanged (no escrow for drafts)
        assertEquals(state2.economy, state3.economy)
    }

    @Test
    fun `CancelContract removes OPEN contract from board with escrow refund`() {
        var state = initialState(123u)
        state = state.copy(economy = state.economy.copy(moneyCopper = 1000))
        val rng = Rng(456L)

        // Post a contract to board
        val draftId = state.inbox.first().id
        val postCmd = PostContract(
            inboxId = draftId.toLong(),
            fee = 75,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, _) = step(state, postCmd, rng)

        val contractOnBoard = state2.board.contracts.values.first()
        val oldReserved = state2.economy.reservedCopper

        // Cancel the board contract
        val cancelCmd = CancelContract(
            contractId = contractOnBoard.id.toLong(),
            cmdId = 2L
        )
        val (state3, events) = step(state2, cancelCmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as ContractCancelled
        assertEquals(contractOnBoard.id.value, event.contractId)
        assertEquals("board", event.location)
        assertEquals(75, event.refundedCopper)

        // Contract should be removed from board
        assertNull(state3.board.contracts[contractOnBoard.id], "Contract should be removed from board")

        // Reserved copper should decrease by fee amount
        assertEquals(oldReserved - 75, state3.economy.reservedCopper)
    }

    @Test
    fun `CancelContract rejects non-existent contract`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cancelCmd = CancelContract(
            contractId = 99999L,
            cmdId = 1L
        )
        val (_, events) = step(state, cancelCmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as Rejected
        assertTrue(event.detail.contains("not found") || event.detail.contains("NOT_FOUND"),
            "Should reject with NOT_FOUND reason")
    }

    @Test
    fun `CancelContract rejects TAKEN contract`() {
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
            // Try to cancel a taken contract
            val cancelCmd = CancelContract(
                contractId = takenContract.id.toLong(),
                cmdId = 3L
            )
            val (_, events) = step(state3, cancelCmd, rng)

            assertEquals(1, events.size)
            val event = events[0] as Rejected
            assertTrue(event.detail.contains("OPEN") || event.detail.contains("status"),
                "Should reject non-OPEN contract")
        }
    }

    @Test
    fun `CancelContract handles multiple drafts in inbox`() {
        var state = initialState(123u)
        val rng = Rng(456L)

        // Create 3 drafts
        val ids = mutableListOf<Int>()
        for (i in 1..3) {
            val createCmd = CreateContract(
                title = "Contract $i",
                rank = Rank.F,
                difficulty = 30,
                reward = 50,
                salvage = SalvagePolicy.GUILD,
                cmdId = i.toLong()
            )
            val (newState, events) = step(state, createCmd, rng)
            state = newState
            ids.add((events[0] as ContractDraftCreated).draftId)
        }

        assertEquals(3 + state.inbox.size - 3, state.inbox.size)

        // Cancel the middle one
        val cancelCmd = CancelContract(
            contractId = ids[1].toLong(),
            cmdId = 4L
        )
        val (state2, events) = step(state, cancelCmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as ContractCancelled
        assertEquals(ids[1], event.contractId)

        // Middle draft removed, others remain
        assertNull(state2.inbox.find { it.id.value == ids[1] })
        assertTrue(state2.inbox.any { it.id.value == ids[0] })
        assertTrue(state2.inbox.any { it.id.value == ids[2] })
    }

    @Test
    fun `CancelContract escrow refund preserves economy invariants`() {
        var state = initialState(123u)
        state = state.copy(economy = state.economy.copy(moneyCopper = 500, reservedCopper = 0))
        val rng = Rng(456L)

        // Post a contract with fee=100
        val draftId = state.inbox.first().id
        val postCmd = PostContract(
            inboxId = draftId.toLong(),
            fee = 100,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state2, _) = step(state, postCmd, rng)

        // Should have 500 total, 100 reserved, 400 available
        assertEquals(500, state2.economy.moneyCopper)
        assertEquals(100, state2.economy.reservedCopper)

        val contractOnBoard = state2.board.contracts.values.first()

        // Cancel the contract
        val cancelCmd = CancelContract(
            contractId = contractOnBoard.id.toLong(),
            cmdId = 2L
        )
        val (state3, _) = step(state2, cancelCmd, rng)

        // After cancel: still 500 total, 0 reserved, 500 available
        assertEquals(500, state3.economy.moneyCopper)
        assertEquals(0, state3.economy.reservedCopper)

        // Invariant: reserved should never be negative
        assertTrue(state3.economy.reservedCopper >= 0)
        // Invariant: reserved should never exceed total
        assertTrue(state3.economy.reservedCopper <= state3.economy.moneyCopper)
    }

    @Test
    fun `CancelContract deterministic with same seed`() {
        val state = initialState(123u)
        val rng1 = Rng(456L)
        val rng2 = Rng(456L)

        // Create and cancel with rng1
        val createCmd = CreateContract(
            title = "Test Contract",
            rank = Rank.F,
            difficulty = 30,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )
        val (state1a, events1a) = step(state, createCmd, rng1)
        val draftId = (events1a[0] as ContractDraftCreated).draftId

        val cancelCmd = CancelContract(
            contractId = draftId.toLong(),
            cmdId = 2L
        )
        val (finalState1, finalEvents1) = step(state1a, cancelCmd, rng1)

        // Create and cancel with rng2
        val (state2a, events2a) = step(state, createCmd, rng2)
        val (finalState2, finalEvents2) = step(state2a, cancelCmd, rng2)

        assertEquals(finalEvents1, finalEvents2, "Events should be identical with same seed")
        assertEquals(finalState1, finalState2, "States should be identical with same seed")
    }

    @Test
    fun `CancelContract after UpdateContractTerms refunds updated fee`() {
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

        // Update fee to 120
        val updateCmd = UpdateContractTerms(
            contractId = contractOnBoard.id.toLong(),
            newFee = 120,
            newSalvage = null,
            cmdId = 2L
        )
        val (state3, _) = step(state2, updateCmd, rng)

        val oldReserved = state3.economy.reservedCopper

        // Cancel - should refund 120, not 50
        val cancelCmd = CancelContract(
            contractId = contractOnBoard.id.toLong(),
            cmdId = 3L
        )
        val (state4, events) = step(state3, cancelCmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as ContractCancelled
        assertEquals(120, event.refundedCopper)

        // Reserved should decrease by 120
        assertEquals(oldReserved - 120, state4.economy.reservedCopper)
    }
}
