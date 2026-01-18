package test

import core.*
import core.primitives.*
import core.state.*
import kotlin.test.*

/**
 * P1 CRITICAL: Command validation tests.
 * Invalid commands could corrupt game state.
 */
class P1_CommandValidationTest {

    @Test
    fun `canApply allows AdvanceDay on any state`() {
        val state = initialState(42u)
        val cmd = AdvanceDay(cmdId = 1L)

        val rejection = canApply(state, cmd)

        assertNull(rejection, "AdvanceDay should always be valid")
    }

    @Test
    fun `canApply rejects PostContract with non-existent inboxId`() {
        val state = initialState(42u)
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = ContractId(999),
            rank = Rank.F,
            fee = 100,
            salvage = SalvagePolicy.GUILD
        )

        val rejection = canApply(state, cmd)

        assertNotNull(rejection, "Should reject non-existent inbox contract")
        assertEquals(RejectReason.NOT_FOUND, rejection.reason)
        assertTrue(rejection.detail.contains("999"))
    }

    @Test
    fun `canApply allows PostContract with valid inboxId`() {
        val state = initialState(42u).copy(
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
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = ContractId(1),
            rank = Rank.F,
            fee = 100,
            salvage = SalvagePolicy.GUILD
        )

        val rejection = canApply(state, cmd)

        assertNull(rejection, "Should accept valid inbox ID")
    }

    @Test
    fun `canApply rejects PostContract with negative fee`() {
        val state = initialState(42u).copy(
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
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = ContractId(1),
            rank = Rank.F,
            fee = -100,
            salvage = SalvagePolicy.GUILD
        )

        val rejection = canApply(state, cmd)

        assertNotNull(rejection, "Should reject negative fee")
        assertEquals(RejectReason.INVALID_ARG, rejection.reason)
        assertTrue(rejection.detail.contains("fee"))
    }

    @Test
    fun `canApply rejects CloseReturn with non-existent activeContractId`() {
        val state = initialState(42u)
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = ActiveContractId(999)
        )

        val rejection = canApply(state, cmd)

        assertNotNull(rejection, "Should reject non-existent active contract")
        assertEquals(RejectReason.NOT_FOUND, rejection.reason)
        assertTrue(rejection.detail.contains("999"))
    }

    @Test
    fun `canApply rejects CloseReturn when active contract is not RETURN_READY`() {
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 0,
                        daysRemaining = 2,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            )
        )
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = ActiveContractId(1)
        )

        val rejection = canApply(state, cmd)

        assertNotNull(rejection, "Should reject CloseReturn on WIP contract")
        assertEquals(RejectReason.INVALID_STATE, rejection.reason)
        assertTrue(rejection.detail.contains("status"))
    }

    @Test
    fun `canApply rejects CloseReturn when return packet does not require close`() {
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 0,
                        daysRemaining = 0,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.RETURN_READY
                    )
                ),
                returns = listOf(
                    ReturnPacket(
                        activeContractId = ActiveContractId(1),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 0,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = false // Does not require close
                    )
                )
            )
        )
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = ActiveContractId(1)
        )

        val rejection = canApply(state, cmd)

        assertNotNull(rejection, "Should reject when return doesn't require close")
        assertEquals(RejectReason.NOT_FOUND, rejection.reason)
    }

    @Test
    fun `canApply allows CloseReturn when all conditions are met`() {
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 0,
                        daysRemaining = 0,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.RETURN_READY
                    )
                ),
                returns = listOf(
                    ReturnPacket(
                        activeContractId = ActiveContractId(1),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 0,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
                    )
                )
            )
        )
        val cmd = CloseReturn(
            cmdId = 1L,
            activeContractId = ActiveContractId(1)
        )

        val rejection = canApply(state, cmd)

        assertNull(rejection, "Should accept valid CloseReturn")
    }

    @Test
    fun `canApply returns consistent results for same inputs`() {
        val state = initialState(42u)
        val cmd = PostContract(
            cmdId = 1L,
            inboxId = ContractId(999),
            rank = Rank.F,
            fee = 100,
            salvage = SalvagePolicy.GUILD
        )

        val rejection1 = canApply(state, cmd)
        val rejection2 = canApply(state, cmd)

        assertEquals(rejection1?.reason, rejection2?.reason)
        assertEquals(rejection1?.detail, rejection2?.detail)
    }

    @Test
    fun `canApply does not modify state`() {
        val state = initialState(42u)
        val cmd = AdvanceDay(cmdId = 1L)

        val stateBefore = state
        canApply(state, cmd)
        val stateAfter = state

        assertEquals(stateBefore, stateAfter, "canApply must not modify state")
    }
}
