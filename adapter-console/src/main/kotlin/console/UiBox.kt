/**
 * ASCII box rendering utility for consistent CLI visual framing.
 *
 * All rendering is pure and deterministic.
 * Width is fixed to 80 chars for stable golden test output.
 */
package console

/**
 * Reusable ASCII card renderer for major outputs.
 * Produces consistent framed layout with no logic changes.
 */
object UiBox {
    private const val WIDTH = 80
    private const val INNER_WIDTH = WIDTH - 4 // Account for "│ " prefix and " │" suffix

    private val TOP_BORDER = "┌${"─".repeat(WIDTH - 2)}┐"
    private val BOTTOM_BORDER = "└${"─".repeat(WIDTH - 2)}┘"
    private val DIVIDER = "├${"─".repeat(WIDTH - 2)}┤"

    /**
     * Renders a framed box with title and rows.
     *
     * @param title Box title (centered in header)
     * @param rows Content rows (each row is left-aligned, wrapped if too long)
     * @return List of lines forming the box
     */
    fun render(title: String, rows: List<String>): List<String> {
        val result = mutableListOf<String>()
        result.add(TOP_BORDER)
        result.add(formatTitleLine(title))
        result.add(DIVIDER)
        for (row in rows) {
            result.addAll(wrapAndFormat(row))
        }
        result.add(BOTTOM_BORDER)
        return result
    }

    /**
     * Renders a simple box with just content (no title).
     */
    fun renderSimple(rows: List<String>): List<String> {
        val result = mutableListOf<String>()
        result.add(TOP_BORDER)
        for (row in rows) {
            result.addAll(wrapAndFormat(row))
        }
        result.add(BOTTOM_BORDER)
        return result
    }

    /**
     * Renders a box with title and sections separated by dividers.
     */
    fun renderWithSections(title: String, sections: List<List<String>>): List<String> {
        val result = mutableListOf<String>()
        result.add(TOP_BORDER)
        result.add(formatTitleLine(title))
        result.add(DIVIDER)

        sections.forEachIndexed { index, section ->
            for (row in section) {
                result.addAll(wrapAndFormat(row))
            }
            if (index < sections.size - 1) {
                result.add(DIVIDER)
            }
        }
        result.add(BOTTOM_BORDER)
        return result
    }

    private fun formatTitleLine(title: String): String {
        val truncated = if (title.length > INNER_WIDTH) title.take(INNER_WIDTH) else title
        val padding = INNER_WIDTH - truncated.length
        val leftPad = padding / 2
        val rightPad = padding - leftPad
        return "│ ${" ".repeat(leftPad)}$truncated${" ".repeat(rightPad)} │"
    }

    private fun wrapAndFormat(text: String): List<String> {
        if (text.isEmpty()) {
            return listOf(formatContentLine(""))
        }

        val lines = mutableListOf<String>()
        var remaining = text

        while (remaining.isNotEmpty()) {
            if (remaining.length <= INNER_WIDTH) {
                lines.add(formatContentLine(remaining))
                break
            }

            // Find break point (prefer space)
            var breakPoint = remaining.lastIndexOf(' ', INNER_WIDTH)
            if (breakPoint <= 0) {
                breakPoint = INNER_WIDTH
            }

            lines.add(formatContentLine(remaining.substring(0, breakPoint)))
            remaining = remaining.substring(breakPoint).trimStart()
        }

        return lines
    }

    private fun formatContentLine(content: String): String {
        val truncated = if (content.length > INNER_WIDTH) content.take(INNER_WIDTH) else content
        val padding = INNER_WIDTH - truncated.length
        return "│ $truncated${" ".repeat(padding)} │"
    }
}
