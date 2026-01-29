package console.render

/**
 * Rendering configuration (pure & deterministic).
 *
 * @param renderWidth Total width of each rendered line.
 * @param useUnicodeBorders If false, uses ASCII-only borders.
 */
data class RenderConfig(
    val renderWidth: Int = 86,
    val useUnicodeBorders: Boolean = true
)

/**
 * Unified box/table renderer.
 *
 * Contract:
 * - Pure and deterministic.
 * - Never emits \r or tabs.
 * - Every produced line length <= cfg.renderWidth.
 */
object BoxRenderer {

    fun box(title: String, bodyLines: List<String>, cfg: RenderConfig): String {
        val b = borders(cfg)
        val innerWidth = innerWidth(cfg)

        val out = StringBuilder()
        out.appendLine(b.top(cfg.renderWidth))
        out.appendLine(contentLine(center(title, innerWidth), cfg))
        out.appendLine(b.div(cfg.renderWidth))
        for (line in bodyLines) {
            for (wrapped in wrap(line, innerWidth)) {
                out.appendLine(contentLine(wrapped, cfg))
            }
        }
        out.appendLine(b.bottom(cfg.renderWidth))
        return out.toString()
    }

    fun boxWithSections(title: String, sections: List<List<String>>, cfg: RenderConfig): String {
        val b = borders(cfg)
        val innerWidth = innerWidth(cfg)

        val out = StringBuilder()
        out.appendLine(b.top(cfg.renderWidth))
        out.appendLine(contentLine(center(title, innerWidth), cfg))
        out.appendLine(b.div(cfg.renderWidth))

        sections.forEachIndexed { idx, section ->
            for (line in section) {
                for (wrapped in wrap(line, innerWidth)) {
                    out.appendLine(contentLine(wrapped, cfg))
                }
            }
            if (idx < sections.size - 1) out.appendLine(b.div(cfg.renderWidth))
        }

        out.appendLine(b.bottom(cfg.renderWidth))
        return out.toString()
    }

    /**
     * Produces label/value rows that wrap values cleanly.
     *
     * Example:
     *   Coin: 123
     *   Very long label: a value that wraps...
     */
    fun labelValueRows(rows: List<Pair<String, String>>, cfg: RenderConfig): List<String> {
        val labelWidth = rows.maxOfOrNull { it.first.length } ?: 0
        return rows.flatMap { (label, value) ->
            labelValueRow(label, value, labelWidth = labelWidth, cfg = cfg)
        }
    }

    fun labelValueRow(label: String, value: String, labelWidth: Int, cfg: RenderConfig): List<String> {
        val innerWidth = innerWidth(cfg)
        val prefix = "${label.padEnd(labelWidth)}: "
        val contPrefix = " ".repeat(prefix.length)

        val available = (innerWidth - prefix.length).coerceAtLeast(1)
        val chunks = wrap(value, available)

        if (chunks.isEmpty()) return listOf(prefix.trimEnd())

        return buildList {
            add(prefix + chunks.first())
            for (i in 1 until chunks.size) add(contPrefix + chunks[i])
        }
    }

    fun wrap(text: String, width: Int): List<String> {
        val clean = text.replace('\t', ' ').replace("\r", "")
        if (clean.isEmpty()) return listOf("")
        if (width <= 1) return clean.map { it.toString() }

        val out = mutableListOf<String>()
        var remaining = clean
        while (remaining.isNotEmpty()) {
            if (remaining.length <= width) {
                out.add(remaining)
                break
            }
            val breakAt = remaining.lastIndexOf(' ', startIndex = width)
            val cut = if (breakAt >= 1) breakAt else width
            out.add(remaining.substring(0, cut).trimEnd())
            remaining = remaining.substring(cut).trimStart()
        }
        return out
    }

    fun ensureNoOverflow(rendered: String, cfg: RenderConfig) {
        // no-op helper for tests; intentionally kept here to share line splitting logic if needed.
        for (line in rendered.split("\n")) {
            if (line.isEmpty()) continue
            require(!line.contains('\r'))
            require(!line.contains('\t'))
            require(line.length <= cfg.renderWidth)
        }
    }

    private fun innerWidth(cfg: RenderConfig): Int =
        (cfg.renderWidth - 4).coerceAtLeast(1)

    private fun contentLine(content: String, cfg: RenderConfig): String {
        val innerWidth = innerWidth(cfg)
        val clean = content.replace('\t', ' ').replace("\r", "")
        val truncated = if (clean.length > innerWidth) clean.take(innerWidth) else clean
        val padding = innerWidth - truncated.length
        // Always pad so borders align; still respects <= renderWidth.
        return "│ $truncated${" ".repeat(padding)} │".replaceBordersIfAscii(cfg)
    }

    private fun center(text: String, width: Int): String {
        val clean = text.replace('\t', ' ').replace("\r", "")
        val truncated = if (clean.length > width) clean.take(width) else clean
        val pad = width - truncated.length
        val left = pad / 2
        val right = pad - left
        return " ".repeat(left) + truncated + " ".repeat(right)
    }

    private data class Borders(
        val tl: Char,
        val tr: Char,
        val bl: Char,
        val br: Char,
        val hz: Char,
        val vt: Char,
        val lj: Char,
        val rj: Char
    ) {
        fun top(width: Int): String = "$tl${hz.toString().repeat((width - 2).coerceAtLeast(0))}$tr"
        fun bottom(width: Int): String = "$bl${hz.toString().repeat((width - 2).coerceAtLeast(0))}$br"
        fun div(width: Int): String = "$lj${hz.toString().repeat((width - 2).coerceAtLeast(0))}$rj"
    }

    private fun borders(cfg: RenderConfig): Borders =
        if (cfg.useUnicodeBorders) {
            Borders('┌', '┐', '└', '┘', '─', '│', '├', '┤')
        } else {
            Borders('+', '+', '+', '+', '-', '|', '+', '+')
        }

    private fun String.replaceBordersIfAscii(cfg: RenderConfig): String {
        if (cfg.useUnicodeBorders) return this
        return this
            .replace('│', '|')
            .replace('┌', '+')
            .replace('┐', '+')
            .replace('└', '+')
            .replace('┘', '+')
            .replace('─', '-')
            .replace('├', '+')
            .replace('┤', '+')
    }
}
