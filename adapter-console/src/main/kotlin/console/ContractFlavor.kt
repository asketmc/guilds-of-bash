/**
 * Contract story flavor text generation (adapter-only).
 *
 * Feature 4: Display short flavor lines for contracts without modifying contract model.
 * Derives story text from existing fields: difficulty, rank, reward, salvagePolicy.
 *
 * Feature 5: Hero return quotes on contract resolution.
 * Maps outcome to fixed text templates.
 */
package console

import core.ContractResolved
import core.primitives.Outcome
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.state.BoardContract
import core.state.ContractDraft
import core.state.GameState
import core.state.Hero
import console.render.BoxRenderer
import console.render.RenderConfig

/**
 * Contract flavor text generator.
 * Pure presentation logic with no core changes.
 */
object ContractFlavor {

    /**
     * Generate flavor line for a contract draft (inbox).
     * Derives from difficulty, rank, and salvage.
     */
    fun forDraft(draft: ContractDraft): String {
        val lines = mutableListOf<String>()

        // Difficulty-based flavor
        lines.add(difficultyFlavor(draft.baseDifficulty))

        // Rank-based flavor (if notable)
        rankFlavor(draft.rankSuggested)?.let { lines.add(it) }

        // Salvage-based flavor
        salvageFlavor(draft.salvage)?.let { lines.add(it) }

        return lines.joinToString(" ")
    }

    /**
     * Generate flavor line for a board contract.
     * Derives from difficulty, rank, fee, and salvage.
     */
    fun forBoard(contract: BoardContract): String {
        val lines = mutableListOf<String>()

        // Difficulty-based flavor
        lines.add(difficultyFlavor(contract.baseDifficulty))

        // Fee-based flavor (if notable)
        feeFlavor(contract.fee, contract.rank)?.let { lines.add(it) }

        // Salvage-based flavor
        salvageFlavor(contract.salvage)?.let { lines.add(it) }

        return lines.joinToString(" ")
    }

    /**
     * Difficulty flavor based on difficulty value.
     * Supports both 1-5 scale (threat-scaling) and 0-100 scale (validation path).
     */
    private fun difficultyFlavor(difficulty: Int): String = when {
        // 0-100 scale interpretation
        difficulty >= 80 -> "A death-defying undertaking for only the bravest souls."
        difficulty >= 60 -> "Dangerous work reported nearby."
        difficulty >= 40 -> "Moderate risk expected."
        difficulty >= 20 -> "A straightforward task for competent adventurers."
        difficulty > 5 -> "Simple work, suitable for novices."

        // 1-5 scale interpretation (threat-scaling generation)
        difficulty >= 5 -> "A death-defying undertaking for only the bravest souls."
        difficulty >= 4 -> "Dangerous work reported nearby."
        difficulty >= 3 -> "Moderate risk expected."
        difficulty >= 2 -> "A straightforward task for competent adventurers."
        else -> "Simple work, suitable for novices."
    }

    /**
     * Rank-based flavor for notable ranks.
     */
    private fun rankFlavor(rank: Rank): String? = when (rank) {
        Rank.S -> "Only legendary heroes need apply."
        Rank.A -> "Veteran adventurers recommended."
        Rank.B -> "Seasoned warriors preferred."
        Rank.F -> "New blood welcome."
        else -> null
    }

    /**
     * Fee-based flavor (relative to rank expectations).
     */
    private fun feeFlavor(fee: Int, rank: Rank): String? {
        val expectedBase = when (rank) {
            Rank.F -> 20
            Rank.E -> 40
            Rank.D -> 60
            Rank.C -> 100
            Rank.B -> 150
            Rank.A -> 250
            Rank.S -> 400
        }

        return when {
            fee >= expectedBase * 2 -> "The pay is generous."
            fee >= expectedBase * 1.5 -> "Fair compensation offered."
            fee <= expectedBase / 2 -> "The reward seems meager for the task."
            else -> null
        }
    }

    /**
     * Salvage policy flavor.
     */
    private fun salvageFlavor(salvage: SalvagePolicy): String? = when (salvage) {
        SalvagePolicy.HERO -> "Heroes claim spoils by tradition."
        SalvagePolicy.GUILD -> "All salvage returns to the guild coffers."
        SalvagePolicy.SPLIT -> "Spoils divided fairly between guild and hero."
    }
}

/**
 * Hero return quote generator.
 *
 * Feature 5: Print one-line hero quote on contract resolution.
 * Maps outcome to fixed text templates.
 */
object HeroQuotes {

    /**
     * Generate a quote for contract resolution.
     *
     * @param event The ContractResolved event
     * @param hero The hero who completed the contract (nullable for generic quote)
     * @return A formatted quote string
     */
    fun forResolution(event: ContractResolved, hero: Hero?): String {
        val heroName = hero?.name ?: "The Hero"
        val quote = quoteForOutcome(event.outcome)
        return "\"$quote\" — $heroName"
    }

    /**
     * Generate a quote for resolution with hero lookup from state.
     */
    fun forResolution(event: ContractResolved, state: GameState): String {
        // Find the hero(es) from the active contract or returns
        val heroId = state.contracts.active
            .find { it.id.value == event.activeContractId }
            ?.heroIds?.firstOrNull()
            ?: state.contracts.returns
                .find { it.activeContractId.value == event.activeContractId }
                ?.heroIds?.firstOrNull()

        val hero = heroId?.let { hid -> state.heroes.roster.find { it.id == hid } }
        return forResolution(event, hero)
    }

    /**
     * Outcome-specific quotes (deterministic, no randomness).
     */
    private fun quoteForOutcome(outcome: Outcome): String = when (outcome) {
        Outcome.SUCCESS -> "Clean job. Pay received."
        Outcome.PARTIAL -> "Could have gone better, but we got it done."
        Outcome.FAIL -> "We do not speak of this."
        Outcome.DEATH, Outcome.MISSING -> "We do not speak of this."
    }

    /**
     * Alternative quotes based on quality (for variety without randomness).
     */
    fun quoteWithQuality(outcome: Outcome, trophiesCount: Int): String = when (outcome) {
        Outcome.SUCCESS -> when {
            trophiesCount > 5 -> "A fine haul! The guild prospers."
            trophiesCount > 0 -> "Clean job. Pay received."
            else -> "Task complete. No salvage this time."
        }
        Outcome.PARTIAL -> when {
            trophiesCount > 0 -> "Not our finest hour, but we brought something back."
            else -> "Could have gone better, but we got it done."
        }
        Outcome.FAIL, Outcome.DEATH, Outcome.MISSING -> "We do not speak of this."
    }
}

/**
 * Renders contract listings with flavor text in framed cards.
 */
object ContractListRenderer {

    /**
     * Render inbox contracts with flavor.
     */
    fun renderInbox(state: GameState, cfg: RenderConfig = RenderConfig(renderWidth = 86, useUnicodeBorders = true)): String {
        val drafts = state.contracts.inbox.sortedBy { it.id.value }
        if (drafts.isEmpty()) {
            return BoxRenderer.box("CONTRACT OFFERS", listOf("No new contract offers at this time."), cfg)
        }

        val rows = mutableListOf<String>()
        for (draft in drafts) {
            rows.add("═══ Offer #${draft.id.value} ═══")
            rows.add("Title: ${draft.title}")
            rows.add("Rank: ${draft.rankSuggested} | Fee Offered: ${draft.feeOffered} copper")
            rows.add("Salvage: ${draft.salvage} | Created: Day ${draft.createdDay}")
            rows.add("")
            rows.add("» ${ContractFlavor.forDraft(draft)}")
            rows.add("")
        }
        return BoxRenderer.box("CONTRACT OFFERS", rows, cfg)
    }

    /**
     * Render inbox contracts with flavor as lines.
     */
    fun renderInboxLines(state: GameState, cfg: RenderConfig = RenderConfig(renderWidth = 86, useUnicodeBorders = true)): List<String> =
        renderInbox(state, cfg).split("\n")

    /**
     * Render board contracts with flavor.
     */
    fun renderBoard(state: GameState, cfg: RenderConfig = RenderConfig(renderWidth = 86, useUnicodeBorders = true)): String {
        val contracts = state.contracts.board.sortedBy { it.id.value }
        if (contracts.isEmpty()) {
            return BoxRenderer.box("GUILD NOTICE BOARD", listOf("The notice board stands empty."), cfg)
        }

        val rows = mutableListOf<String>()
        for (contract in contracts) {
            rows.add("═══ Contract #${contract.id.value} ═══")
            rows.add("Title: ${contract.title}")
            rows.add("Rank: ${contract.rank} | Fee: ${contract.fee} copper | Status: ${contract.status}")
            rows.add("Salvage: ${contract.salvage} | Posted: Day ${contract.postedDay}")
            rows.add("")
            rows.add("» ${ContractFlavor.forBoard(contract)}")
            rows.add("")
        }
        return BoxRenderer.box("GUILD NOTICE BOARD", rows, cfg)
    }

    /**
     * Render board contracts with flavor as lines.
     */
    fun renderBoardLines(state: GameState, cfg: RenderConfig = RenderConfig(renderWidth = 86, useUnicodeBorders = true)): List<String> =
        renderBoard(state, cfg).split("\n")
}
