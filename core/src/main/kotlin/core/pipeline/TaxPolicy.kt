// FILE: core/src/main/kotlin/core/pipeline/TaxPolicy.kt
package core.pipeline

import core.BalanceSettings
import core.calculateTaxAmount

/**
 * Tax policy decision model.
 *
 * ## Semantic Ownership
 * Answers exactly one question: **Why shutdown triggered?**
 *
 * ## Stability Gradient
 * STABLE: Pure calculation logic with explicit rules.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT touch economy directly.
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object TaxPolicy {

    /**
     * Evaluates tax status at end of day.
     *
     * @param currentDay Current day index.
     * @param taxDueDay Day when tax is due.
     * @param taxAmountDue Current tax amount due.
     * @param taxPenalty Current penalty amount.
     * @param taxMissedCount Number of consecutive misses.
     * @param guildRank Current guild rank for next tax calculation.
     * @return [TaxEvaluation] with computed changes or null if no tax action needed.
     */
    fun evaluateEndOfDay(
        currentDay: Int,
        taxDueDay: Int,
        taxAmountDue: Int,
        taxPenalty: Int,
        taxMissedCount: Int,
        guildRank: Int
    ): TaxEvaluation? {
        if (currentDay < taxDueDay) return null

        val totalDue = taxAmountDue + taxPenalty

        return if (totalDue > 0) {
            val penaltyAdded = (totalDue * BalanceSettings.TAX_PENALTY_PERCENT) / 100
            val newTaxPenalty = taxPenalty + penaltyAdded
            val newMissedCount = taxMissedCount + 1
            val shutdownTriggered = newMissedCount >= BalanceSettings.TAX_MAX_MISSED
            val newTaxDueDay = taxDueDay + BalanceSettings.TAX_INTERVAL_DAYS
            val newTaxAmount = calculateTaxAmount(guildRank, BalanceSettings.TAX_BASE_AMOUNT)

            TaxEvaluation(
                type = TaxEvaluationType.MISSED,
                amountDue = totalDue,
                penaltyAdded = penaltyAdded,
                newTaxPenalty = newTaxPenalty,
                newMissedCount = newMissedCount,
                shutdownTriggered = shutdownTriggered,
                newTaxDueDay = newTaxDueDay,
                newTaxAmountDue = newTaxAmount
            )
        } else {
            val newTaxDueDay = taxDueDay + BalanceSettings.TAX_INTERVAL_DAYS
            val newTaxAmount = calculateTaxAmount(guildRank, BalanceSettings.TAX_BASE_AMOUNT)

            TaxEvaluation(
                type = TaxEvaluationType.DUE_SCHEDULED,
                amountDue = newTaxAmount,
                penaltyAdded = 0,
                newTaxPenalty = taxPenalty,
                newMissedCount = taxMissedCount,
                shutdownTriggered = false,
                newTaxDueDay = newTaxDueDay,
                newTaxAmountDue = newTaxAmount
            )
        }
    }

    /**
     * Computes tax payment application.
     *
     * @param paymentAmount Amount being paid.
     * @param currentTaxDue Current tax amount due.
     * @param currentPenalty Current penalty amount.
     * @return [TaxPaymentResult] with amounts applied.
     */
    fun computePayment(
        paymentAmount: Int,
        currentTaxDue: Int,
        currentPenalty: Int
    ): TaxPaymentResult {
        var remaining = paymentAmount
        var penaltyRemaining = currentPenalty
        var taxDueRemaining = currentTaxDue

        // Apply to penalty first
        val appliedToPenalty = minOf(remaining, penaltyRemaining)
        penaltyRemaining -= appliedToPenalty
        remaining -= appliedToPenalty

        // Then to tax
        val appliedToTax = minOf(remaining, taxDueRemaining)
        taxDueRemaining -= appliedToTax

        val totalRemaining = taxDueRemaining + penaltyRemaining
        val isComplete = totalRemaining == 0

        return TaxPaymentResult(
            appliedToPenalty = appliedToPenalty,
            appliedToTax = appliedToTax,
            newTaxDue = taxDueRemaining,
            newPenalty = penaltyRemaining,
            remainingDue = totalRemaining,
            isComplete = isComplete
        )
    }
}

/**
 * Type of tax evaluation result.
 */
enum class TaxEvaluationType {
    /** Tax was due and missed. */
    MISSED,
    /** No outstanding tax, new tax scheduled. */
    DUE_SCHEDULED
}

/**
 * Result of end-of-day tax evaluation.
 */
data class TaxEvaluation(
    val type: TaxEvaluationType,
    val amountDue: Int,
    val penaltyAdded: Int,
    val newTaxPenalty: Int,
    val newMissedCount: Int,
    val shutdownTriggered: Boolean,
    val newTaxDueDay: Int,
    val newTaxAmountDue: Int
)

/**
 * Result of tax payment computation.
 */
data class TaxPaymentResult(
    val appliedToPenalty: Int,
    val appliedToTax: Int,
    val newTaxDue: Int,
    val newPenalty: Int,
    val remainingDue: Int,
    val isComplete: Boolean
)
