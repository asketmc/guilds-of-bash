package console

import console.render.RenderConfig
import console.render.StewardReportInput
import console.render.StewardReportRenderer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class StewardReportGoldenTest {

    @Test
    fun `renderStewardReport structure and determinism`() {
        val input = StewardReportInput(
            day = 7,
            revision = 12,
            stateHash = "abc123",
            coin = 1200,
            reserved = 300,
            available = 900,
            trophies = 14,
            inboxOffers = 3,
            postedToday = 2,
            active = 1,
            returnsNeedingAttention = 0,
            stability = 10,
            reputation = 5,
            rank = 2,
            completedTotal = 9,
            taxDueDay = 10,
            owed = 250,
            penalties = 0,
            missed = 0,
            concerns = emptyList()
        )

        val cfg = RenderConfig(renderWidth = 86, useUnicodeBorders = true)
        val out1 = StewardReportRenderer.renderStewardReport(input, cfg)
        val out2 = StewardReportRenderer.renderStewardReport(input, cfg)

        // Determinism.
        assertEquals(out1, out2)

        // No forbidden characters.
        assertFalse(out1.contains('\r'))
        assertFalse(out1.contains('\t'))

        // Newline discipline.
        assertTrue(out1.endsWith("\n"))

        // Line length invariant.
        out1.split("\n").filter { it.isNotEmpty() }.forEach { line ->
            assertTrue(line.length <= cfg.renderWidth, "Line overflow: len=${line.length}")
        }

        // Structural anchors (stable, spec-driven).
        assertTrue(out1.startsWith("hash=abc123\n"))
        assertTrue(out1.contains("STEWARD REPORT"))
        assertTrue(out1.contains("Day 7 of operations, Revision 12"))
        assertTrue(out1.contains("TREASURY:"))
        assertTrue(out1.contains("OPERATIONS:"))
        assertTrue(out1.contains("GUILD STANDING:"))
        assertTrue(out1.contains("CROWN OBLIGATIONS:"))
        assertTrue(out1.contains("CONCERNS:"))
        assertTrue(out1.contains("(none)"))
    }

    @Test
    fun `renderStewardReport narrow width no overflow`() {
        val input = StewardReportInput(
            day = 1,
            revision = 1,
            stateHash = "hash",
            coin = 1,
            reserved = 0,
            available = 1,
            trophies = 0,
            inboxOffers = 20,
            postedToday = 0,
            active = 0,
            returnsNeedingAttention = 2,
            stability = -1,
            reputation = null,
            rank = 1,
            completedTotal = 0,
            taxDueDay = 3,
            owed = 9999,
            penalties = 12,
            missed = 1,
            concerns = listOf(
                "A very long concern sentence that should wrap cleanly inside the report box without exceeding width."
            )
        )

        val cfg = RenderConfig(renderWidth = 60, useUnicodeBorders = false)
        val out = StewardReportRenderer.renderStewardReport(input, cfg)

        assertFalse(out.contains('\r'))
        assertFalse(out.contains('\t'))

        out.split("\n").filter { it.isNotEmpty() }.forEach { line ->
            assertTrue(line.length <= cfg.renderWidth, "Line overflow: len=${line.length}")
        }
    }
}
