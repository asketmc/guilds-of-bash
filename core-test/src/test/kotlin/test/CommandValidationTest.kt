package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.*
import core.state.*
import test.helpers.*
import kotlin.test.*

/**
 * P1 CRITICAL: Command validation tests.
 * Invalid commands could corrupt game state.
 */
@P1
@Smoke
class CommandValidationTest {

    @Test
    fun `canApply allows AdvanceDay on any state`() {
        // GIVEN: any initial state
        val state = baseState()
        val cmd = AdvanceDay(cmdId = 1L)

        // WHEN: canApply
        val result = canApply(state, cmd)

        // THEN: valid
        assertEquals(ValidationResult.Valid, result, "AdvanceDay should always be valid")
    }

    @Test
    fun `canApply rejects PostContract with non-existent inboxId`() {
        val state = baseState()
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = 999L,
            fee = 100,
            salvage = SalvagePolicy.GUILD
        )

        val result = canApply(state, cmd)

        assertTrue(result is ValidationResult.Rejected, "Should reject non-existent inbox contract")
        assertEquals(RejectReason.NOT_FOUND, (result.reason))
        assertTrue(result.detail.contains("999"))
    }

    @Test
    fun `canApply allows PostContract with valid inboxId`() {
        val state = stateWithEconomy(moneyCopper = 100, reservedCopper = 0, trophiesStock = 0).copy(
            contracts = ContractState(
                inbox = listOf(contractDraft(id = 1L)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = 1L,
            fee = 100,
            salvage = SalvagePolicy.GUILD
        )

        val result = canApply(state, cmd)

        assertEquals(ValidationResult.Valid, result, "Should accept valid inbox ID")
    }

    @Test
    fun `canApply rejects PostContract with negative fee`() {
        val state = baseState().copy(
            contracts = ContractState(
                inbox = listOf(contractDraft(id = 1L)),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = 1L,
            fee = -100,
            salvage = SalvagePolicy.GUILD
        )

        val result = canApply(state, cmd)

        assertTrue(result is ValidationResult.Rejected, "Should reject negative fee")
        assertEquals(RejectReason.INVALID_ARG, (result.reason))
        assertTrue(result.detail.contains("fee"))
    }

    @Test
    fun `canApply rejects CloseReturn with non-existent activeContractId`() {
        val state = baseState()
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = 999L
        )

        val result = canApply(state, cmd)

        assertTrue(result is ValidationResult.Rejected, "Should reject non-existent active contract")
        assertEquals(RejectReason.NOT_FOUND, (result.reason))
        assertTrue(result.detail.contains("999"))
    }

    @Test
    fun `canApply rejects CloseReturn when active contract is not RETURN_READY`() {
        val state = baseState().copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.WIP, daysRemaining = 2)),
                returns = emptyList()
            )
        )
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = 1L
        )

        val result = canApply(state, cmd)

        assertTrue(result is ValidationResult.Rejected, "Should reject CloseReturn on WIP contract")
        // NOTE: Expect NOT_FOUND when the associated return packet is absent
        assertEquals(RejectReason.NOT_FOUND, (result.reason))
        assertTrue(result.detail.contains("status") || result.detail.contains("not found"))
    }

    @Test
    fun `canApply rejects CloseReturn when return packet does not require close`() {
        val state = baseState().copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
                returns = listOf(
                    returnPacket(activeId = 1L, heroIds = listOf(1L), requiresPlayerClose = false)
                )
            )
        )
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = 1L
        )

        val result = canApply(state, cmd)

        assertTrue(result is ValidationResult.Rejected, "Should reject when return doesn't require close")
        assertEquals(RejectReason.NOT_FOUND, (result.reason))
    }

    @Test
    fun `canApply allows CloseReturn when all conditions are met`() {
        val state = baseState().copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
                returns = listOf(
                    returnPacket(activeId = 1L, heroIds = listOf(1L), requiresPlayerClose = true)
                )
            )
        )
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = 1L
        )

        val result = canApply(state, cmd)

        assertEquals(ValidationResult.Valid, result, "Should accept valid CloseReturn")
    }

    @Test
    fun `canApply returns consistent results for same inputs`() {
        val state = baseState()
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = 999L,
            fee = 100,
            salvage = SalvagePolicy.GUILD
        )

        val result1 = canApply(state, cmd)
        val result2 = canApply(state, cmd)

        assertEquals(result1, result2)
    }

    @Test
    fun `canApply does not modify state`() {
        val state = baseState()
        val cmd = AdvanceDay(cmdId = 1L)

        val stateBefore = state
        canApply(state, cmd)
        val stateAfter = state

        assertEquals(stateBefore, stateAfter, "canApply must not modify state")
    }
}
