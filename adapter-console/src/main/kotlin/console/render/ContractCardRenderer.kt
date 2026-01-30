package console.render

/**
 * Minimal view DTO used for contract cards.
 */
data class ContractView(
    val id: Long,
    val title: String,
    val rank: String,
    val fee: String,
    val status: String,
    val salvage: String,
    val postedDay: Int,
    val summaryLines: List<String>
)

object ContractCardRenderer {

    fun renderContractCards(contracts: List<ContractView>, cfg: RenderConfig): String {
        if (contracts.isEmpty()) {
            return BoxRenderer.box("CONTRACTS", listOf("(none)"), cfg)
        }

        val rows = mutableListOf<String>()
        for ((idx, c) in contracts.withIndex()) {
            rows.add(cardHeader("Contract #${c.id}", cfg))
            rows.addAll(BoxRenderer.labelValueRow("Title", c.title, labelWidth = 7, cfg = cfg))
            rows.add("Rank: ${c.rank} | Fee: ${c.fee} | Status: ${c.status}")
            rows.add("Salvage: ${c.salvage} | Posted: Day ${c.postedDay}")
            rows.add("")
            val wrappedSummary = c.summaryLines
                .flatMap { BoxRenderer.wrap(it, (cfg.renderWidth - 4).coerceAtLeast(1)) }
            rows.addAll(wrappedSummary)
            if (idx < contracts.size - 1) rows.add("")
        }

        return BoxRenderer.box("CONTRACTS", rows, cfg)
    }

    private fun cardHeader(title: String, cfg: RenderConfig): String {
        val innerWidth = (cfg.renderWidth - 4).coerceAtLeast(1)
        val core = "=== $title ==="
        if (core.length >= innerWidth) return core.take(innerWidth)
        val pad = innerWidth - core.length
        val right = pad
        return core + "=".repeat(right)
    }
}
