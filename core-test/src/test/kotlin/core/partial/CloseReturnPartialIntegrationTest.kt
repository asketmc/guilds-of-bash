package core.partial

import core.CloseReturn
import core.rng.Rng
import core.state.initialState
import core.step
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CloseReturnPartialIntegrationTest {

    @Test
    fun `CloseReturn on PARTIAL applies partial resolution and emits observable event`() {
        val base = initialState(seed = 42u)

        val prepared = TestStateFactory.stateWithPartialReturnReady(
            baseState = base,
            activeId = 1001L,
            normalMoneyValueCopper = 101
        )

        val rng = Rng(123L)

        val result = step(prepared, CloseReturn(activeContractId = 1001L, cmdId = 1L), rng)

        assertEquals(
            prepared.economy.moneyCopper + 50,
            result.state.economy.moneyCopper,
            "PARTIAL close must apply resolver (floor half of 101 = 50)"
        )

        assertTrue(
            result.events.any { it::class.simpleName == "ReturnClosed" } || result.events.any { it::class.simpleName == "CommandRejected" },
            "CloseReturn must never be silent; expected ReturnClosed or CommandRejected (depending on policy)"
        )
    }
}
