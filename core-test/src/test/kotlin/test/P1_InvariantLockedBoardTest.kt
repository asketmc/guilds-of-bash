package test

import core.*
import core.invariants.InvariantId
import core.invariants.verifyInvariants
import core.primitives.*
import core.rng.Rng
import core.state.*
import kotlin.test.*

/**
 * P1 CRITICAL: LOCKED board invariant tests.
 * Tests that LOCKED boards are properly unlocked when all actives are closed.
 */
class P1_InvariantLockedBoardTest {

    @Test
    fun `closing last return unlocks board and no invariant violation`() {
        // GIVEN a board contract taken to active, resolved to return, then close it
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 0,
                trophiesStock = 0
            ),
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Test",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
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
                    ReturnPacket(
                        boardContractId = ContractId(1),
                        heroIds = listOf(HeroId(1)),
                        activeContractId = ActiveContractId(1),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 2,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
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

        // Verify board is LOCKED before close
        val boardBefore = state.contracts.board.first { it.id.value == 1 }
        assertEquals(BoardStatus.LOCKED, boardBefore.status, "Board should be LOCKED initially")

        // WHEN CloseReturn
        val cmd = CloseReturn(activeContractId = 1L, cmdId = 1L)
        val rng = Rng(100L)
        val result = step(state, cmd, rng)
        state = result.state

        // THEN board status is not LOCKED
        val boardAfter = state.contracts.board.first { it.id.value == 1 }
        assertNotEquals(BoardStatus.LOCKED, boardAfter.status, "Board should not be LOCKED after close")
        // NOTE: BoardStatus.RETURN_READY is not used; expect COMPLETED instead
        assertEquals(BoardStatus.COMPLETED, boardAfter.status, "Board should be COMPLETED after close")

        // Active status допускаем RETURN_READY или CLOSED
        val activeAfter = state.contracts.active.first { it.id.value == 1 }
        assertTrue(activeAfter.status == ActiveStatus.CLOSED || activeAfter.status == ActiveStatus.RETURN_READY, "Active should be CLOSED or RETURN_READY")

        // Step events do not include InvariantViolated with that invariantId
        val invariantViolations = result.events.filterIsInstance<InvariantViolated>()
        val lockedBoardViolations = invariantViolations.filter {
            it.invariantId == InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE
        }
        assertEquals(0, lockedBoardViolations.size, "Should have no LOCKED board violations in events")

        // verifyInvariants(finalState) contains no such violations
        val stateViolations = verifyInvariants(state)
        val stateLockedBoardViolations = stateViolations.filter {
            it.invariantId == InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE
        }
        assertEquals(0, stateLockedBoardViolations.size, "Should have no LOCKED board violations in final state")
    }

    @Test
    fun `multiple actives on same board unlocks only when all closed`() {
        // Test that board remains LOCKED if any active is not CLOSED
        val rng = Rng(100L)
        var state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                reservedCopper = 0,
                trophiesStock = 0
            ),
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Test",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
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
                    ),
                    ActiveContract(
                        id = ActiveContractId(2),
                        boardContractId = ContractId(1),
                        takenDay = 1,
                        daysRemaining = 0,
                        heroIds = listOf(HeroId(2)),
                        status = ActiveStatus.RETURN_READY
                    )
                ),
                returns = listOf(
                    ReturnPacket(
                        boardContractId = ContractId(1),
                        heroIds = listOf(HeroId(1)),
                        activeContractId = ActiveContractId(1),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 1,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
                    ),
                    ReturnPacket(
                        boardContractId = ContractId(1),
                        heroIds = listOf(HeroId(1)),
                        activeContractId = ActiveContractId(2),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 1,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
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
                    ),
                    Hero(
                        id = HeroId(2),
                        name = "Hero #2",
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

        // Close first active
        val result1 = step(state, CloseReturn(activeContractId = 1L, cmdId = 1L), rng)
        state = result1.state

        // Board should still be LOCKED (active 2 is not CLOSED)
        val boardAfterFirst = state.contracts.board.first { it.id.value == 1 }
        assertEquals(BoardStatus.LOCKED, boardAfterFirst.status, "Board should remain LOCKED after first close")

        // No violations yet
        val violations1 = verifyInvariants(state)
        val lockedViolations1 = violations1.filter {
            it.invariantId == InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE
        }
        assertEquals(0, lockedViolations1.size, "Should have no violations after first close")

        // Close second active
        val result2 = step(state, CloseReturn(activeContractId = 2L, cmdId = 2L), rng)
        state = result2.state

        // Now board should be COMPLETED
        val boardAfterSecond = state.contracts.board.first { it.id.value == 1 }
        assertEquals(BoardStatus.COMPLETED, boardAfterSecond.status, "Board should be COMPLETED after all closed")

        // Still no violations
        val violations2 = verifyInvariants(state)
        val lockedViolations2 = violations2.filter {
            it.invariantId == InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE
        }
        assertEquals(0, lockedViolations2.size, "Should have no violations after all closed")
    }

    @Test
    fun `multiple actives on same board triggers invariant violation`() {
        // GIVEN a board with two actives referencing it
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Test",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
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
                    ),
                    ActiveContract(
                        id = ActiveContractId(2),
                        boardContractId = ContractId(1),
                        takenDay = 1,
                        daysRemaining = 0,
                        heroIds = listOf(HeroId(2)),
                        status = ActiveStatus.RETURN_READY
                    )
                ),
                returns = listOf(
                    ReturnPacket(
                        boardContractId = ContractId(1),
                        heroIds = listOf(HeroId(1)),
                        activeContractId = ActiveContractId(1),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 2,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
                    ),
                    ReturnPacket(
                        boardContractId = ContractId(1),
                        heroIds = listOf(HeroId(2)),
                        activeContractId = ActiveContractId(2),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 2,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
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
                    ),
                    Hero(
                        id = HeroId(2),
                        name = "Hero #2",
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
        // WHEN: Проверяем инварианты
        val violations = verifyInvariants(state)
        assertTrue(violations.any { it.invariantId == InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE },
            "Should trigger invariant violation for multiple actives on same board")
    }

    @Test
    fun `board remains locked if active is WIP or RETURN_READY`() {
        // Test that board stays LOCKED while any active is not CLOSED
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Test",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.LOCKED
                    )
                ),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 1,
                        daysRemaining = 1,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
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

        // Board is LOCKED and active is WIP - this should pass invariants
        val violations = verifyInvariants(state)
        val lockedViolations = violations.filter {
            it.invariantId == InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE
        }
        assertEquals(0, lockedViolations.size, "WIP active should satisfy invariant")
    }

    @Test
    fun `completed board does not trigger locked board invariant`() {
        // Test that COMPLETED board does not trigger the LOCKED board invariant
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Test",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        status = BoardStatus.COMPLETED
                    )
                ),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 1,
                        daysRemaining = 0,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.CLOSED
                    )
                ),
                returns = emptyList()
            ),
            heroes = HeroState(
                roster = listOf(
                    Hero(
                        id = HeroId(1),
                        name = "Hero #1",
                        rank = Rank.F,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(greed = 50, honesty = 50, courage = 50),
                        status = HeroStatus.AVAILABLE,
                        historyCompleted = 1
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        // COMPLETED board should not trigger LOCKED board invariant
        val violations = verifyInvariants(state)
        val lockedBoardViolations = violations.filter {
            it.invariantId == InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE
        }
        assertEquals(0, lockedBoardViolations.size, "COMPLETED board should not trigger LOCKED board invariant")
    }
}
