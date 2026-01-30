// FILE: core/src/main/kotlin/core/pipeline/EconomySettlement.kt
package core.pipeline

import core.partial.PartialOutcomeResolver
import core.partial.PartialResolutionInput
import core.partial.TrophiesQuality
import core.primitives.Outcome
import core.primitives.Quality
import core.primitives.SalvagePolicy
import core.state.BoardContract
import core.state.EconomyState

/**
 * Economy settlement model.
 *
 * ## Semantic Ownership
 * Answers exactly one question: **Why money / escrow / trophies changed by X?**
 *
 * ## Stability Gradient
 * STABLE: Pure calculation logic with explicit rules.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 * - Integer-only math for stability.
 *
 * ## Boundary Rules
 * - Must NOT decide outcomes.
 * - Must NOT check proof policy.
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object EconomySettlement {

    /**
     * Computes economy delta for a contract closure (auto-close path).
     *
     * @param outcome The resolved outcome.
     * @param board The board contract being closed (nullable for safety).
     * @param trophiesCount Final trophies count (after theft).
     * @param currentEconomy Current economy state for delta calculation.
     * @return [EconomyDelta] containing all monetary/trophy changes.
     */
    fun computeAutoCloseDelta(
        outcome: Outcome,
        board: BoardContract?,
        trophiesCount: Int,
        currentEconomy: EconomyState
    ): EconomyDelta {
        val fee = board?.fee ?: 0
        val clientDeposit = board?.clientDeposit ?: 0
        val salvage = board?.salvage

        val trophiesGuildGets = computeSalvageTrophies(salvage, trophiesCount)

        // Reserved holds clientDeposit only
        val reservedDelta = -clientDeposit

        // Money calculation depends on outcome
        val moneyDelta = when (outcome) {
            Outcome.FAIL, Outcome.DEATH -> 0 // No fee paid on failure or death
            else -> -fee // Guild pays hero the full fee
        }

        return EconomyDelta(
            moneyDelta = moneyDelta,
            reservedDelta = reservedDelta,
            trophiesDelta = trophiesGuildGets
        )
    }

    /**
     * Computes economy delta for manual return closure (player close path).
     *
     * @param outcome The resolved outcome.
     * @param board The board contract being closed (nullable for safety).
     * @param trophiesCount Final trophies count (after theft).
     * @param trophiesQuality Quality of returned trophies.
     * @param suspectedTheft Whether theft was suspected.
     * @param currentEconomy Current economy state.
     * @return [EconomyDelta] containing all monetary/trophy changes.
     */
    fun computeManualCloseDelta(
        outcome: Outcome,
        board: BoardContract?,
        trophiesCount: Int,
        trophiesQuality: Quality,
        suspectedTheft: Boolean,
        currentEconomy: EconomyState
    ): EconomyDelta {
        val fee = board?.fee ?: 0
        val clientDeposit = board?.clientDeposit ?: 0
        val salvage = board?.salvage

        val trophiesGuildGets = computeSalvageTrophies(salvage, trophiesCount)
        val reservedDelta = -clientDeposit

        val moneyDelta = if (outcome == Outcome.PARTIAL) {
            // PARTIAL policy: apply deterministic normalization via resolver
            val resolved = PartialOutcomeResolver.resolve(
                PartialResolutionInput(
                    outcome = outcome,
                    normalMoneyValueCopper = fee,
                    trophiesCount = trophiesCount,
                    trophiesQuality = TrophiesQuality.fromCoreQuality(trophiesQuality),
                    suspectedTheft = suspectedTheft
                )
            )
            resolved.moneyValueCopper
        } else {
            when (outcome) {
                Outcome.FAIL, Outcome.DEATH -> 0
                else -> -fee
            }
        }

        return EconomyDelta(
            moneyDelta = moneyDelta,
            reservedDelta = reservedDelta,
            trophiesDelta = trophiesGuildGets
        )
    }

    /**
     * Computes trophies that go to the guild based on salvage policy.
     */
    private fun computeSalvageTrophies(salvage: SalvagePolicy?, trophiesCount: Int): Int {
        return when (salvage) {
            SalvagePolicy.GUILD -> trophiesCount
            SalvagePolicy.HERO -> 0
            SalvagePolicy.SPLIT -> trophiesCount / 2
            null -> 0
        }
    }

    /**
     * Computes economy delta for trophy sale.
     */
    fun computeTrophySaleDelta(amountToSell: Int): EconomyDelta {
        return EconomyDelta(
            moneyDelta = amountToSell, // 1:1 exchange rate
            reservedDelta = 0,
            trophiesDelta = -amountToSell
        )
    }

    /**
     * Computes economy delta for contract posting.
     *
     * Accounting model:
     * - Guild receives clientDeposit from the client (money increases)
     * - clientDeposit is locked in escrow (reserved increases)
     *
     * @param clientDeposit Client's deposit to be locked.
     */
    fun computePostContractDelta(clientDeposit: Int): EconomyDelta {
        return EconomyDelta(
            moneyDelta = clientDeposit, // Guild receives client deposit
            reservedDelta = clientDeposit, // Deposit is locked until completion
            trophiesDelta = 0
        )
    }

    /**
     * Computes economy delta for contract cancellation.
     *
     * Accounting model:
     * - clientDeposit is released from escrow (reserved decreases)
     * - money is reduced by clientDeposit (refund to client)
     *
     * @param clientDeposit Client's deposit to be refunded.
     */
    fun computeCancelContractDelta(clientDeposit: Int): EconomyDelta {
        return EconomyDelta(
            moneyDelta = -clientDeposit, // Guild returns deposit to client
            reservedDelta = -clientDeposit, // Release escrow
            trophiesDelta = 0
        )
    }
}

/**
 * Settlement output representing economy changes.
 *
 * This DTO is pure data and can be tested in isolation.
 */
data class EconomyDelta(
    /** Change to moneyCopper (positive = gain, negative = loss). */
    val moneyDelta: Int,
    /** Change to reservedCopper. */
    val reservedDelta: Int,
    /** Change to trophiesStock. */
    val trophiesDelta: Int
) {
    /**
     * Applies this delta to an existing economy state.
     */
    fun applyTo(economy: EconomyState): EconomyState {
        return economy.copy(
            moneyCopper = economy.moneyCopper + moneyDelta,
            reservedCopper = economy.reservedCopper + reservedDelta,
            trophiesStock = economy.trophiesStock + trophiesDelta
        )
    }
}
