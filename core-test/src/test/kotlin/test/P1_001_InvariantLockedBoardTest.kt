// FILE: core-test/src/test/kotlin/test/P1_InvariantLockedBoardTest.kt
package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.invariants.InvariantId
import core.primitives.ActiveStatus
import core.primitives.HeroStatus
import core.primitives.BoardStatus
import kotlin.test.Test

/**
 * P1 CRITICAL: LOCKED board invariant tests.
 * Tests that LOCKED boards are properly unlocked when all actives are closed.
 */
class P1_InvariantLockedBoardTest {

    private val lockedBoardInvariant = InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE

    @Test
    fun `closing last return unlocks board and no invariant violation`() {
        val fx = lockedBoardFixture(
            actives = listOf(
                active(
                    id = 1L,
                    heroIds = listOf(1L),
                    status = ActiveStatus.RETURN_READY
                )
            ),
            returns = listOf(
                returnPacket(
                    activeId = 1L,
                    heroIds = listOf(1L),
                    trophiesCount = 2
                )
            ),
            heroes = listOf(hero(1L))
        )

        assertBoardStatus(fx.initial, BoardStatus.LOCKED)

        val res = closeReturn(fx.initial, activeContractId = 1L, cmdId = 1L, rng = fx.rng)

        assertBoardStatus(res.state, BoardStatus.COMPLETED)
        assertActiveStatusIn(res.state, activeContractId = 1L, allowed = setOf(ActiveStatus.CLOSED, ActiveStatus.RETURN_READY))

        assertNoInvariantViolation(res.events, lockedBoardInvariant)
        assertNoLockedBoardViolations(res.state)
    }

    @Test
    fun `multiple actives on same board unlocks only when all closed`() {
        val fx = lockedBoardFixture(
            actives = listOf(
                active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY),
                active(id = 2L, heroIds = listOf(2L), status = ActiveStatus.RETURN_READY)
            ),
            returns = listOf(
                returnPacket(activeId = 1L, heroIds = listOf(1L)),
                returnPacket(activeId = 2L, heroIds = listOf(2L))
            ),
            heroes = listOf(hero(1L), hero(2L))
        )

        val r1 = closeReturn(fx.initial, activeContractId = 1L, cmdId = 1L, rng = fx.rng)
        assertBoardStatus(r1.state, BoardStatus.LOCKED)
        assertNoLockedBoardViolations(r1.state)

        val r2 = closeReturn(r1.state, activeContractId = 2L, cmdId = 2L, rng = fx.rng)
        assertBoardStatus(r2.state, BoardStatus.COMPLETED)
        assertNoLockedBoardViolations(r2.state)
    }

    @Test
    fun `multiple actives on same board triggers invariant violation`() {
        val state = lockedBoardState(
            actives = listOf(
                active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY),
                active(id = 2L, heroIds = listOf(2L), status = ActiveStatus.RETURN_READY)
            ),
            returns = listOf(
                returnPacket(activeId = 1L, heroIds = listOf(1L), trophiesCount = 2),
                returnPacket(activeId = 2L, heroIds = listOf(2L), trophiesCount = 2)
            ),
            heroes = listOf(hero(1L), hero(2L))
        )

        assertHasLockedBoardViolation(state)
    }

    @Test
    fun `board remains locked if active is WIP or RETURN_READY`() {
        val state = lockedBoardState(
            actives = listOf(
                active(
                    id = 1L,
                    heroIds = listOf(1L),
                    status = ActiveStatus.WIP,
                    daysRemaining = 1
                )
            ),
            returns = emptyList(),
            heroes = listOf(hero(1L))
        )

        assertNoLockedBoardViolations(state)
    }

    @Test
    fun `completed board does not trigger locked board invariant`() {
        val state = lockedBoardState(
            boardStatus = BoardStatus.COMPLETED,
            actives = listOf(
                active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.CLOSED)
            ),
            returns = emptyList(),
            heroes = listOf(
                hero(id = 1L, status = HeroStatus.AVAILABLE, historyCompleted = 1)
            )
        )

        assertNoLockedBoardViolations(state)
    }
}
