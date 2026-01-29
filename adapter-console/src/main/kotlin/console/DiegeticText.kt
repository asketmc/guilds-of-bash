/**
 * Diegetic (in-world styled) help and status rendering.
 *
 * Feature 3: Replace raw help and status text with in-world styled documents.
 * - help → "SCRIBE'S NOTE" card listing same commands
 * - status → "STEWARD REPORT" card using existing state fields
 */
package console

import core.rng.Rng
import core.state.GameState
import core.primitives.ActiveStatus
import console.render.BoxRenderer
import console.render.RenderConfig

/**
 * Renders in-world styled help document ("SCRIBE'S NOTE").
 * Contains the same commands as raw help but with diegetic framing.
 */
object DiegeticHelp {
    private const val TITLE = "SCRIBE'S NOTE"

    fun render(cfg: RenderConfig = RenderConfig(renderWidth = 86, useUnicodeBorders = true)): String {
        val rows = listOf(
            "Herein lies the ledger of guild commands, as recorded by the Scribe:",
            "",
            "GENERAL OPERATIONS:",
            "  help .............. Consult this note",
            "  status ............ Request the Steward's report",
            "  quit .............. Close the guild ledger",
            "",
            "CONTRACT MANAGEMENT:",
            "  list inbox ........ Review incoming contract offers",
            "  list board ........ Survey posted contracts",
            "  list active ....... Check ongoing missions",
            "  list returns ...... Inspect returning parties",
            "",
            "DAILY OPERATIONS:",
            "  day, advance ...... Advance to the next dawn",
            "  auto <n> .......... Advance multiple days",
            "",
            "CONTRACT ACTIONS:",
            "  post <id> <fee> <salvage> ... Post a contract to the board",
            "  create <title> <rank> <diff> <reward> [salvage]",
            "                    ... Draft a new contract",
            "  update <id> [fee=N] [salvage=X] ... Modify posted terms",
            "  cancel <id> ....... Withdraw a contract",
            "  close <id> ........ Process a returning party",
            "",
            "ECONOMY:",
            "  sell <amount> ..... Sell trophies to merchants",
            "  tax pay <amount> .. Render tribute to the Crown",
            "",
            "May your quill stay sharp and your ledger balanced.",
            "                                    — The Guild Scribe"
        )
        return BoxRenderer.box(TITLE, rows, cfg)
    }

    fun renderLines(cfg: RenderConfig = RenderConfig(renderWidth = 86, useUnicodeBorders = true)): List<String> =
        render(cfg).split("\n")
}

/**
 * Renders in-world styled status document ("STEWARD REPORT").
 * Uses existing state fields to present information in diegetic style.
 */
object DiegeticStatus {
    private const val TITLE = "STEWARD REPORT"

    /**
     * Render the steward report from current game state.
     *
     * Includes:
     * - day, gold, board/active/returns, stability, threat
     * - 2-3 deterministic "concerns" derived from state
     *
     * @param rng Reserved for future deterministic variation (currently unused).
     */
    @Suppress("UNUSED_PARAMETER")
    fun render(state: GameState, rng: Rng, cfg: RenderConfig = RenderConfig(renderWidth = 86, useUnicodeBorders = true)): String {
        val returnsNeedingClose = state.contracts.returns.count { it.requiresPlayerClose }
        val activeWipCount = state.contracts.active.count { it.status == ActiveStatus.WIP }
        val availableCopper = state.economy.moneyCopper - state.economy.reservedCopper

        val sections = mutableListOf<List<String>>()

        // Header section
        sections.add(
            listOf(
                "To the Guildmaster, a summary of affairs:",
                "Day ${state.meta.dayIndex} of operations, Revision ${state.meta.revision}"
            )
        )

        // Treasury section
        sections.add(
            listOf(
                "TREASURY:",
                "  Coin in coffers: ${state.economy.moneyCopper} copper",
                "  Reserved for contracts: ${state.economy.reservedCopper} copper",
                "  Available funds: $availableCopper copper",
                "  Trophies in storage: ${state.economy.trophiesStock}"
            )
        )

        // Operations section
        sections.add(
            listOf(
                "OPERATIONS:",
                "  Contract offers awaiting review: ${state.contracts.inbox.size}",
                "  Contracts posted to board: ${state.contracts.board.size}",
                "  Active missions in progress: $activeWipCount",
                "  Returning parties requiring attention: $returnsNeedingClose"
            )
        )

        // Standing section
        sections.add(
            listOf(
                "GUILD STANDING:",
                "  Regional stability: ${state.region.stability}%",
                "  Guild reputation: ${state.guild.reputation}",
                "  Current rank: ${state.guild.guildRank}",
                "  Contracts completed (total): ${state.guild.completedContractsTotal}"
            )
        )

        // Tax section
        sections.add(
            listOf(
                "CROWN OBLIGATIONS:",
                "  Tax due by day: ${state.meta.taxDueDay}",
                "  Amount owed: ${state.meta.taxAmountDue} copper",
                "  Accumulated penalties: ${state.meta.taxPenalty} copper",
                "  Missed payments: ${state.meta.taxMissedCount}"
            )
        )

        // Concerns section (deterministic based on state)
        val concerns = generateConcerns(state, returnsNeedingClose)
        if (concerns.isNotEmpty()) {
            sections.add(listOf("CONCERNS:") + concerns.map { "  • $it" })
        }

        // Footer
        sections.add(
            listOf(
                "",
                "Respectfully submitted,",
                "                                  — The Guild Steward"
            )
        )

        return BoxRenderer.boxWithSections(TITLE, sections, cfg)
    }

    fun renderLines(state: GameState, rng: Rng, cfg: RenderConfig = RenderConfig(renderWidth = 86, useUnicodeBorders = true)): List<String> =
        render(state, rng, cfg).split("\n")

    /**
     * Generate 2-3 deterministic concerns based on state thresholds.
     */
    private fun generateConcerns(state: GameState, returnsNeedingClose: Int): List<String> {
        val concerns = mutableListOf<String>()

        // Stability concern
        when {
            state.region.stability < 20 ->
                concerns.add("The region teeters on the brink of chaos. Urgent action required.")
            state.region.stability < 40 ->
                concerns.add("Regional unrest grows. The people look to us for protection.")
            state.region.stability < 60 ->
                concerns.add("Minor disturbances reported. Vigilance is advised.")
        }

        // Returns concern
        if (returnsNeedingClose > 0) {
            val plural = if (returnsNeedingClose > 1) "parties await" else "party awaits"
            concerns.add("$returnsNeedingClose returning $plural your signature on the completion forms.")
        }

        // Financial concerns
        val availableFunds = state.economy.moneyCopper - state.economy.reservedCopper
        when {
            availableFunds < 0 ->
                concerns.add("CRITICAL: Debts exceed available funds. Bankruptcy looms.")
            availableFunds < 50 ->
                concerns.add("Treasury runs dangerously low. Consider selling trophies.")
        }

        // Tax concerns
        val daysUntilTax = state.meta.taxDueDay - state.meta.dayIndex
        when {
            state.meta.taxMissedCount >= 2 ->
                concerns.add("WARNING: Crown inspectors grow impatient. One more missed payment...")
            daysUntilTax <= 3 && state.meta.taxAmountDue > 0 ->
                concerns.add("Tax deadline approaches in $daysUntilTax days.")
        }

        // Board congestion
        if (state.contracts.board.size >= 10) {
            concerns.add("The notice board grows crowded. Heroes may overlook postings.")
        }

        return concerns.take(3)
    }
}
