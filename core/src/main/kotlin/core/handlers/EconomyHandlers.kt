// FILE: core/src/main/kotlin/core/handlers/EconomyHandlers.kt
package core.handlers

import core.*
import core.pipeline.EconomySettlement
import core.pipeline.TaxPolicy
import core.primitives.*
import core.rng.Rng
import core.state.*

/**
 * Economy command handlers.
 *
 * ## Semantic Ownership
 * Handles all economy-related commands: trophy sales, tax payments.
 *
 * ## Visibility
 * Internal to core module - only Reducer.kt should call these.
 */

/**
 * Sells trophies for copper.
 *
 * Why:
 * - Trophy liquidation is the main short-term liquidity tool.
 *
 * How:
 * - Converts trophies into copper using a fixed exchange rate.
 * - Emits a single event that fully explains the transaction.
 */
@Suppress("UNUSED_PARAMETER", "ReturnCount")
internal fun handleSellTrophies(
    state: GameState,
    cmd: SellTrophies,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val total = state.economy.trophiesStock
    if (total <= 0) return state

    val amountToSell = if (cmd.amount <= 0) total else minOf(cmd.amount, total)
    if (amountToSell <= 0) return state

    // Settlement: Trophy sale
    val economyDelta = EconomySettlement.computeTrophySaleDelta(amountToSell)

    val newState = state.copy(
        economy = economyDelta.applyTo(state.economy)
    )

    ctx.emit(
        TrophySold(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            amount = amountToSell,
            moneyGained = amountToSell  // 1:1 exchange rate
        )
    )

    return newState
}

/**
 * Pays tax debt.
 *
 * Why:
 * - Tax payment is a risk-reduction lever.
 * - Penalties must be paid before principal to prevent gaming.
 *
 * How:
 * - Applies payment to penalty first, then to the base due.
 * - Emits the remaining due so adapters do not infer debt.
 */
@Suppress("UNUSED_PARAMETER")
internal fun handlePayTax(
    state: GameState,
    cmd: PayTax,
    rng: Rng,
    ctx: SeqContext
): GameState {
    if (cmd.amount <= 0) return state

    val availableMoney = state.economy.moneyCopper
    if (availableMoney < cmd.amount) return state

    // Settlement: Tax payment
    val paymentResult = TaxPolicy.computePayment(
        paymentAmount = cmd.amount,
        currentTaxDue = state.meta.taxAmountDue,
        currentPenalty = state.meta.taxPenalty
    )

    val newState = state.copy(
        economy = state.economy.copy(moneyCopper = availableMoney - cmd.amount),
        meta = state.meta.copy(
            taxAmountDue = paymentResult.newTaxDue,
            taxPenalty = paymentResult.newPenalty,
            taxMissedCount = if (paymentResult.isComplete) 0 else state.meta.taxMissedCount
        )
    )

    ctx.emit(
        TaxPaid(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            amountPaid = cmd.amount,
            amountDue = paymentResult.remainingDue,
            isPartialPayment = !paymentResult.isComplete
        )
    )

    return newState
}
