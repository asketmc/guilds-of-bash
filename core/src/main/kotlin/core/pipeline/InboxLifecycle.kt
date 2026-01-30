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

            // Sample client deposit using rank-based pricing
            val clientDeposit = ContractPricing.sampleClientDepositGp(Rank.F, rng)

            drafts.add(
                ContractDraft(
                    id = ContractId(draftId),
                    createdDay = currentDay,
                    nextAutoResolveDay = currentDay + BalanceSettings.AUTO_RESOLVE_INTERVAL_DAYS,
                    title = "Request #$draftId",
                    rankSuggested = Rank.F,
                    feeOffered = 0,
                    salvage = SalvagePolicy.GUILD,
                    baseDifficulty = baseDifficulty,
                    proofHint = "proof",
                    clientDeposit = clientDeposit
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
 */
data class InboxGenerationResult(
    val drafts: List<ContractDraft>,
    val contractIds: IntArray,
    val nextContractId: Int
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is InboxGenerationResult) return false
        return drafts == other.drafts && contractIds.contentEquals(other.contractIds) && nextContractId == other.nextContractId
    }

    override fun hashCode(): Int {
        var result = drafts.hashCode()
        result = 31 * result + contractIds.contentHashCode()
        result = 31 * result + nextContractId
        return result
    }
}
