// FILE: core/src/main/kotlin/core/pipeline/InboxLifecycle.kt
package core.pipeline

import core.BalanceSettings
import core.calculateThreatLevel
import core.calculateBaseDifficulty
import core.flavour.ContractPricing
import core.primitives.ContractId
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.ContractDraft

/**
 * Inbox lifecycle model.
 *
 * ## Semantic Ownership
 * Answers: **What contracts should be generated?**
 *
 * ## Stability Gradient
 * STABLE: Pure generation logic with explicit rules.
 *
 * ## Determinism
 * - RNG usage is explicit and ordered.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state directly.
 */
object InboxLifecycle {

    /**
     * Generates new inbox drafts for the day.
     *
     * ## Money units
     * - `feeOffered` is stored in **copper** (Int).
     * - `clientDeposit` is stored in **copper** (Int).
     * - Values are produced via [ContractPricing.samplePayoutMoney] /
     *   [ContractPricing.sampleClientDepositMoney].
     *
     * @param count Number of drafts to generate.
     * @param currentDay Current day index.
     * @param stability Current region stability.
     * @param startingContractId Starting ID for new contracts.
     * @param rng Deterministic RNG.
     * @return [InboxGenerationResult] with generated drafts and next ID.
     */
    fun generateDrafts(
        count: Int,
        currentDay: Int,
        stability: Int,
        startingContractId: Int,
        rng: Rng
    ): InboxGenerationResult {
        val drafts = ArrayList<ContractDraft>(count)
        val contractIds = IntArray(count)
        var nextId = startingContractId

        for (i in 0 until count) {
            val draftId = nextId++
            contractIds[i] = draftId

            val threatLevel = calculateThreatLevel(stability)
            val baseDifficulty = calculateBaseDifficulty(threatLevel, rng)

            // Sample payout and client deposit using rank-based pricing (FP-ECON-02)
            val payout = ContractPricing.samplePayoutMoney(Rank.F, rng)
            val clientDeposit = ContractPricing.sampleClientDepositMoney(payout, rng)

            drafts.add(
                ContractDraft(
                    id = ContractId(draftId),
                    createdDay = currentDay,
                    nextAutoResolveDay = currentDay + BalanceSettings.AUTO_RESOLVE_INTERVAL_DAYS,
                    title = "Request #$draftId",
                    rankSuggested = Rank.F,
                    feeOffered = payout.copper,  // Now using sampled payout in copper!
                    salvage = SalvagePolicy.GUILD,
                    baseDifficulty = baseDifficulty,
                    proofHint = "proof",
                    clientDeposit = clientDeposit.copper
                )
            )
        }

        return InboxGenerationResult(
            drafts = drafts,
            contractIds = contractIds,
            nextContractId = nextId
        )
    }
}

/**
 * Result of inbox generation.
 *
 * @property drafts Generated drafts.
 * @property contractIds Raw integer IDs generated for the drafts (same order as [drafts]).
 * @property nextContractId Next available contract ID after generation.
 */
data class InboxGenerationResult(
    val drafts: List<ContractDraft>,
    val contractIds: IntArray,
    val nextContractId: Int
) {
    /**
     * Value equality that compares [contractIds] by content.
     *
     * @param other Object to compare.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InboxGenerationResult) return false
        return drafts == other.drafts &&
            contractIds.contentEquals(other.contractIds) &&
            nextContractId == other.nextContractId
    }

    /** Hash code that uses [IntArray.contentHashCode]. */
    override fun hashCode(): Int {
        var result = drafts.hashCode()
        result = 31 * result + contractIds.contentHashCode()
        result = 31 * result + nextContractId
        return result
    }
}
