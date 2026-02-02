package test

import core.primitives.BoardStatus
import test.helpers.closeReturn
import test.helpers.hero
import test.helpers.lockedBoardFixture
import test.helpers.returnPacket
import kotlin.test.Test
import kotlin.test.assertTrue
import test.helpers.assertBoardAbsent
import test.helpers.assertNoLockedBoardViolations

@P1
class ArchiveContractTest {

    @Test
    fun `closing last return moves board to archive and preserves invariants`() {
        val fx = lockedBoardFixture(
            actives = listOf(
                test.helpers.active(id = 1L, heroIds = listOf(1L), status = core.primitives.ActiveStatus.RETURN_READY)
            ),
            returns = listOf(
                returnPacket(activeId = 1L, heroIds = listOf(1L), trophiesCount = 2)
            ),
            heroes = listOf(hero(1L))
        )

        // Precondition: board is LOCKED
        // Execute manual close
        val res = closeReturn(fx.initial, activeContractId = 1L, cmdId = 1L, rng = fx.rng)

        // The board contract should no longer be present on the active board
        assertBoardAbsent(res.state, boardContractId = 1)

        // The board contract should be present in the archive with COMPLETED status
        assertTrue(
            res.state.contracts.archive.any { it.id.value == 1 && it.status == BoardStatus.COMPLETED },
            "Expected archive to contain completed board contract id=1; archiveIds=${res.state.contracts.archive.map { it.id.value }}"
        )

        // Invariants: locked-board invariant must not be triggered
        assertNoLockedBoardViolations(res.state)
    }
}
