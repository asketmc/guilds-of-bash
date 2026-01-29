package core.partial

import core.primitives.Outcome
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PartialOutcomeResolverTest {

    @Test
    fun `PARTIAL halves normal money value using floor integer math`() {
        val input = PartialResolutionInput(
            outcome = Outcome.PARTIAL,
            normalMoneyValueCopper = 101,
            trophiesCount = 7,
            trophiesQuality = TrophiesQuality.OK,
            suspectedTheft = false
        )

        val out = PartialOutcomeResolver.resolve(input)

        assertEquals(50, out.moneyValueCopper, "Expected floor(101/2)=50 for PoC PARTIAL rule")
        assertEquals(Outcome.PARTIAL, out.outcome, "Outcome must remain PARTIAL")
        assertTrue(out.flags.contains(PartialResolutionFlag.PARTIAL_APPLIED), "Expected PARTIAL_APPLIED flag")
    }
}
