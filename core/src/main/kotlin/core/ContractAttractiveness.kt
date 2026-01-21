package core

import core.primitives.SalvagePolicy
import core.state.BoardContract
import core.state.ContractDraft
import core.state.Hero

/**
 * Result of evaluating a contract's attractiveness to a hero.
 *
 * @property score Higher values indicate more attractive contracts.
 *                 Negative scores indicate the hero would refuse the contract.
 */
data class ContractAttractiveness(
    val score: Int
)

/**
 * Evaluates how attractive a board contract is to a specific hero.
 *
 * Score calculation factors:
 * - Base profit from fee and salvage rights
 * - Risk penalty if difficulty exceeds hero's comfort zone
 * - Personality modifiers (greed increases salvage value, honesty reduces theft temptation)
 * - Courage affects risk tolerance
 *
 * Decision threshold:
 * - score < 0: hero refuses contract
 * - score >= 0: hero willing to take contract
 *
 * @param hero The hero evaluating the contract
 * @param board The board contract being evaluated
 * @param difficulty Base difficulty from the original draft (1 = easy, higher = harder)
 * @return ContractAttractiveness with computed score
 */
fun evaluateContractForHero(
    hero: Hero,
    board: BoardContract,
    difficulty: Int
): ContractAttractiveness {
    // 1. Base profit score from fee
    val feeScore = board.fee / 10

    // 2. Salvage value (influenced by greed)
    val salvageScore = when (board.salvage) {
        SalvagePolicy.GUILD -> {
            // No direct trophy benefit, hero dislikes this if greedy
            val greedPenalty = (hero.traits.greed - 50) / 5  // -10..+10
            -greedPenalty  // Greedy heroes penalize GUILD salvage
        }
        SalvagePolicy.HERO -> {
            // Full trophy ownership, value scales with expected loot
            val baseValue = difficulty * 5  // Harder contracts = more trophies
            val greedBonus = (hero.traits.greed - 50) / 10  // 0..+5 for greedy heroes
            baseValue + greedBonus
        }
        SalvagePolicy.SPLIT -> {
            // Compromise: moderate value
            val baseValue = difficulty * 2
            val greedBonus = (hero.traits.greed - 50) / 20  // 0..+2
            baseValue + greedBonus
        }
    }

    // 3. Risk assessment
    val heroRankLevel = hero.rank.ordinal  // F=0, E=1, D=2, etc.
    val riskThreshold = (heroRankLevel + 1) * 2  // F can handle up to ~2, E up to ~4, etc.
    val riskPenalty = if (difficulty > riskThreshold) {
        // Too dangerous: apply courage-modified penalty
        val basePenalty = (difficulty - riskThreshold) * 15
        val courageMod = (50 - hero.traits.courage) / 10  // Cowards add more penalty
        basePenalty + courageMod
    } else {
        0
    }

    // 4. Combine all factors
    val finalScore = feeScore + salvageScore - riskPenalty

    return ContractAttractiveness(score = finalScore)
}

/**
 * Helper to extract difficulty from a contract draft.
 * This is used during contract evaluation in the pickup phase.
 *
 * @param draft The contract draft to extract difficulty from
 * @return The base difficulty value
 */
fun getDifficultyFromDraft(draft: ContractDraft): Int {
    return draft.baseDifficulty
}

/**
 * Helper to find the corresponding draft for a board contract.
 * Used to retrieve difficulty during hero decision-making.
 *
 * Note: In the current PoC, drafts are removed from inbox when posted to board.
 * For MVP, we'll need to either:
 * 1. Store difficulty in BoardContract directly, OR
 * 2. Maintain a difficulty lookup table
 *
 * For now, this returns a default difficulty based on rank.
 *
 * @param board The board contract
 * @return Estimated difficulty (1-5 scale)
 */
fun estimateDifficultyFromBoard(board: BoardContract): Int {
    // Temporary heuristic until we store difficulty in BoardContract
    return when (board.rank) {
        core.primitives.Rank.F -> 1
        core.primitives.Rank.E -> 2
        core.primitives.Rank.D -> 3
        core.primitives.Rank.C -> 4
        core.primitives.Rank.B -> 5
        core.primitives.Rank.A -> 6
        core.primitives.Rank.S -> 8
    }
}
