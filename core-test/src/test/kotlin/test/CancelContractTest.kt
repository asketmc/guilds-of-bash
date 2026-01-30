package test

import core.*
import core.invariants.InvariantId
import core.primitives.*
import core.rng.Rng
import core.state.initialState
import test.helpers.*
import kotlin.test.*

/**
 * P2: CancelContract Command Tests
 *
 * Validates R1 lifecycle command: CancelContract
 * - Cancelling drafts from inbox
 * - Cancelling OPEN contracts from board (escrow release)
 * - Rejections for missing / non-OPEN contracts
 */
@P2
class CancelContractTest {

    @Test
    fun `CancelContract removes draft from inbox without refund`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val draftId = state.inbox.first().id.value

        val r = step(state, CancelContract(contractId = draftId.toLong(), cmdId = 1L), rng)
        assertStepOk(r.events, "Cancel draft")

        val cancelled = r.events.filterIsInstance<ContractCancelled>().single()
        assertEquals(draftId, cancelled.contractId)
        assertEquals("inbox", cancelled.location.toString().lowercase())
        assertEquals(0, cancelled.refundedCopper)

        assertInboxAbsent(r.state, draftId)
        assertEquals(state.economy, r.state.economy, "Economy must not change when cancelling a draft")
    }

    @Test
    fun `CancelContract removes OPEN contract from board with escrow refund`() {
        val rng = Rng(456L)
        val deposit = 50

        // State with a draft that has a client deposit
        var state = stateWithEconomy(moneyCopper = 1000, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = core.state.ContractState(
                inbox = listOf(contractDraft(id = 1L, clientDeposit = deposit)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // Post a contract to board (fee=75, deposit=50)
        val post = step(
            state,
            PostContract(
                inboxId = 1L,
                fee = 75,
                salvage = SalvagePolicy.GUILD,
                cmdId = 1L
            ),
            rng
        )
        assertStepOk(post.events, "PostContract")
        val state2 = post.state

        // After post: money = 1000 + 50 = 1050, reserved = 50
        assertEquals(1000 + deposit, state2.economy.moneyCopper)
        assertEquals(deposit, state2.economy.reservedCopper)

        val contractOnBoard = state2.board.first()

        // Cancel the board contract
        val r = step(
            state2,
            CancelContract(contractId = contractOnBoard.id.value.toLong(), cmdId = 2L),
            rng
        )
        assertStepOk(r.events, "Cancel board contract")
        val state3 = r.state

        val cancelled = r.events.filterIsInstance<ContractCancelled>().single()
        assertEquals(contractOnBoard.id.value, cancelled.contractId)
        assertEquals("board", cancelled.location.toString().lowercase())
        assertEquals(deposit, cancelled.refundedCopper, "Should refund client deposit")

        // Contract should be removed from board
        assertBoardAbsent(state3, contractOnBoard.id.value)

        // After cancel: money = 1050 - 50 = 1000, reserved = 0
        assertEquals(1000, state3.economy.moneyCopper)
        assertEquals(0, state3.economy.reservedCopper)
    }

    @Test
    fun `CancelContract rejects non-existent contract`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val r = step(state, CancelContract(contractId = 99999L, cmdId = 1L), rng)

        // Prefer canonical enum-based rejection check
        assertSingleRejection(r.events, RejectReason.NOT_FOUND, "Cancel non-existent contract")
    }

    @Test
    fun `CancelContract rejects TAKEN or non-OPEN contract`() {
        var state = initialState(123u).copy(
            economy = initialState(123u).economy.copy(moneyCopper = 1000, reservedCopper = 0)
        )
        val rng = Rng(456L)

        val boardBeforeIds = state.board.map { it.id.value }.toSet()

        // Post a contract
        val draftId = state.inbox.first().id.value
        val post = step(
            state,
            PostContract(
                inboxId = draftId.toLong(),
                fee = 50,
                salvage = SalvagePolicy.GUILD,
                cmdId = 1L
            ),
            rng
        )
        assertStepOk(post.events, "PostContract")
        val postedState = post.state
        val postedBoardContract = postedState.board.first { it.id.value !in boardBeforeIds }

        // Force status to LOCKED to simulate TAKEN (deterministic, avoids relying on day-tick AI)
        val lockedState = postedState.copy(
            contracts = postedState.contracts.copy(
                board = postedState.board.map { bc ->
                    if (bc.id.value == postedBoardContract.id.value) bc.copy(status = BoardStatus.LOCKED) else bc
                }
            )
        )

        val r = step(
            lockedState,
            CancelContract(contractId = postedBoardContract.id.value.toLong(), cmdId = 2L),
            rng
        )

        val rejections = r.events.filterIsInstance<CommandRejected>()
        assertTrue(rejections.isNotEmpty(), "Should reject cancelling non-OPEN (LOCKED/TAKEN) contract, got events=${r.events}")

        // Keep fuzzy detail check to avoid overfitting on RejectReason
        val detail = rejections.first().detail
        assertTrue(
            detail.contains("OPEN", ignoreCase = true) ||
                    detail.contains("LOCKED", ignoreCase = true) ||
                    detail.contains("status", ignoreCase = true),
            "Rejection detail should mention OPEN/status, got: $detail"
        )
    }

    @Test
    fun `CancelContract handles multiple drafts in inbox`() {
        var state = initialState(123u)
        val rng = Rng(456L)

        // Create 3 drafts (in addition to any seeded drafts)
        val ids = mutableListOf<Int>()
        for (i in 1..3) {
            val r = step(
                state,
                CreateContract(
                    title = "Contract $i",
                    rank = Rank.F,
                    difficulty = 30,
                    reward = 50,
                    salvage = SalvagePolicy.GUILD,
                    cmdId = i.toLong()
                ),
                rng
            )
            assertStepOk(r.events, "CreateContract#$i")
            state = r.state

            val created = r.events.filterIsInstance<ContractDraftCreated>().single()
            ids.add(created.draftId)
        }

        // Cancel the middle one
        val rCancel = step(state, CancelContract(contractId = ids[1].toLong(), cmdId = 4L), rng)
        assertStepOk(rCancel.events, "Cancel middle draft")

        val cancelled = rCancel.events.filterIsInstance<ContractCancelled>().single()
        assertEquals(ids[1], cancelled.contractId)

        // Middle draft removed, others remain
        assertInboxAbsent(rCancel.state, ids[1])
        assertInboxPresent(rCancel.state, ids[0])
        assertInboxPresent(rCancel.state, ids[2])
    }

    @Test
    fun `CancelContract escrow refund preserves economy invariants`() {
        val rng = Rng(456L)
        val deposit = 100

        // State with a draft that has a client deposit
        var state = stateWithEconomy(moneyCopper = 500, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = core.state.ContractState(
                inbox = listOf(contractDraft(id = 1L, clientDeposit = deposit)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // Post a contract with fee=150, deposit=100
        val post = step(
            state,
            PostContract(
                inboxId = 1L,
                fee = 150,
                salvage = SalvagePolicy.GUILD,
                cmdId = 1L
            ),
            rng
        )
        assertStepOk(post.events, "PostContract")
        val state2 = post.state

        // After post: money = 500 + 100 = 600, reserved = 100, available = 500
        assertEquals(600, state2.economy.moneyCopper)
        assertEquals(100, state2.economy.reservedCopper)

        val contractOnBoard = state2.board.first()

        // Cancel the contract
        val rCancel = step(
            state2,
            CancelContract(contractId = contractOnBoard.id.value.toLong(), cmdId = 2L),
            rng
        )
        assertStepOk(rCancel.events, "Cancel board contract")
        val state3 = rCancel.state

        // After cancel: money = 600 - 100 = 500, reserved = 0, available = 500
        assertEquals(500, state3.economy.moneyCopper)
        assertEquals(0, state3.economy.reservedCopper)

        // Invariant: reserved should never be negative
        assertNoViolations(state3, InvariantId.ECONOMY__RESERVED_NON_NEGATIVE, "reserved negative")

        // Basic sanity: reserved within [0..money]
        assertTrue(state3.economy.reservedCopper in 0..state3.economy.moneyCopper)
    }

    @Test
    fun `CancelContract deterministic with same seed`() {
        val state = initialState(123u)

        val draftId = state.inbox.first().id.value.toLong()

        val rng1 = Rng(456L)
        val rng2 = Rng(456L)

        val r1 = step(state, CancelContract(contractId = draftId, cmdId = 1L), rng1)
        val r2 = step(state, CancelContract(contractId = draftId, cmdId = 1L), rng2)

        assertEquals(r1.events, r2.events, "Events should be identical with same seed")
        assertEquals(r1.state, r2.state, "States should be identical with same seed")
    }

    @Test
    fun `CancelContract after UpdateContractTerms refunds client deposit not fee`() {
        val rng = Rng(456L)
        val deposit = 50

        // State with a draft that has a client deposit
        var state = stateWithEconomy(moneyCopper = 1000, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = core.state.ContractState(
                inbox = listOf(contractDraft(id = 1L, clientDeposit = deposit)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        // Post a contract with fee=50, deposit=50
        val post = step(
            state,
            PostContract(
                inboxId = 1L,
                fee = 50,
                salvage = SalvagePolicy.GUILD,
                cmdId = 1L
            ),
            rng
        )
        assertStepOk(post.events, "PostContract")
        val state2 = post.state

        val contractOnBoard = state2.board.first()
        val contractId = contractOnBoard.id.value.toLong()

        // Update fee to 120 (but clientDeposit stays at 50)
        val upd = step(
            state2,
            UpdateContractTerms(
                contractId = contractId,
                newFee = 120,
                newSalvage = null,
                cmdId = 2L
            ),
            rng
        )
        assertStepOk(upd.events, "Update terms")
        val state3 = upd.state

        // Reserved is still just the client deposit
        assertEquals(deposit, state3.economy.reservedCopper)

        // Cancel - should refund clientDeposit (50), not the updated fee (120)
        val rCancel = step(state3, CancelContract(contractId = contractId, cmdId = 3L), rng)
        assertStepOk(rCancel.events, "Cancel after update")
        val state4 = rCancel.state

        val cancelled = rCancel.events.filterIsInstance<ContractCancelled>().single()
        assertEquals(deposit, cancelled.refundedCopper, "Should refund client deposit, not fee")

        // Reserved should be 0 after cancel
        assertEquals(0, state4.economy.reservedCopper)
    }
}
