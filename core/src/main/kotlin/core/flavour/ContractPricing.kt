// FILE: core/src/main/kotlin/core/flavour/ContractPricing.kt
package core.flavour

import core.BalanceSettings
import core.primitives.Money
import core.primitives.MoneyCopper
import core.primitives.Rank
import core.rng.Rng
import java.math.BigDecimal

/**
 * Rank-based deterministic pricing for contract drafts.
 *
 * ## Role
 * - Converts target contract rank into (a) sampled payout and (b) clientDeposit contribution.
 *
 * ## Contract
 * - All outputs are Int GP, >= 0.
 * - Output payout is clamped to rank band caps/floors from BalanceSettings.
 * - Deterministic: depends only on (rank, rng draw stream).
 *
 * ## Stability Gradient
 * STABLE: Pure sampling logic with explicit rules from BalanceSettings.
 *
 * ## Determinism
 * - Uses explicit RNG draws in fixed order.
 * - Same seed + same rank → identical results.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state.
 *
 * ## Notes
 * - Non-linearity is approximated by piecewise bands + explicit tail (no floating lognormal yet).
 */
object ContractPricing {

    /**
     * Sample the payout value for a contract in copper units.
     *
     * This is the NEW API (FP-ECON-02) that returns money in copper, eliminating
     * the GP→copper unit mismatch.
     *
     * @param rank Target contract rank
     * @param rng Deterministic RNG
     * @return Sampled payout in copper (>= 0)
     */
    fun samplePayoutMoney(rank: Rank, rng: Rng): MoneyCopper {
        val payoutGp = when (rank) {
            Rank.F -> rng.nextIntInclusive(BalanceSettings.PAYOUT_F_MIN, BalanceSettings.PAYOUT_F_MAX)
            Rank.E -> rng.nextIntInclusive(BalanceSettings.PAYOUT_E_MIN, BalanceSettings.PAYOUT_E_MAX)
            Rank.D -> rng.nextIntInclusive(BalanceSettings.PAYOUT_D_MIN, BalanceSettings.PAYOUT_D_MAX)
            Rank.C -> rng.nextIntInclusive(BalanceSettings.PAYOUT_C_MIN, BalanceSettings.PAYOUT_C_MAX)
            Rank.B -> rng.nextIntInclusive(BalanceSettings.PAYOUT_B_MIN, BalanceSettings.PAYOUT_B_MAX)
            Rank.A -> sampleAWithTail(rng)
            Rank.S -> rng.nextIntInclusive(BalanceSettings.PAYOUT_S_MIN, BalanceSettings.PAYOUT_S_MAX)
        }
        // Convert GP to copper: 1 GP = 100 copper
        return Money.fromGoldDecimal(BigDecimal(payoutGp))
    }

    /**
     * Sample the client's deposit contribution in copper units.
     *
     * This is the NEW API (FP-ECON-02) that computes deposits in copper,
     * preventing truncation bugs (e.g., 1 GP * 50% → 50 copper, not 0).
     *
     * @param payout The sampled payout in copper
     * @param rng Deterministic RNG
     * @return Client deposit in copper (>= 0, <= payout)
     */
    fun sampleClientDepositMoney(payout: MoneyCopper, rng: Rng): MoneyCopper {
        val roll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
        if (roll >= BalanceSettings.CLIENT_PAYS_CHANCE_PERCENT) return Money.ZERO

        // Calculate deposit as fraction of payout in copper (no truncation)
        return Money.mulFractionBp(payout, BalanceSettings.CLIENT_PAYS_FRACTION_BP)
    }

    /**
     * Sample the "economic value" of the quest for the requester (gp/quest).
     * This is not directly paid out; it feeds deposit logic and future balancing.
     *
     * @deprecated Use samplePayoutMoney() instead (FP-ECON-02). This method
     *   returns GP as Int which causes unit mismatch bugs.
     * @param rank Target contract rank.
     * @param rng Deterministic RNG.
     * @return Sampled payout value in GP (>= 0).
     */
    @Deprecated(
        message = "Use samplePayoutMoney() for copper-based calculations",
        replaceWith = ReplaceWith("samplePayoutMoney(rank, rng)")
    )
    fun samplePayoutGp(rank: Rank, rng: Rng): Int {
        return when (rank) {
            Rank.F -> rng.nextIntInclusive(BalanceSettings.PAYOUT_F_MIN, BalanceSettings.PAYOUT_F_MAX)
            Rank.E -> rng.nextIntInclusive(BalanceSettings.PAYOUT_E_MIN, BalanceSettings.PAYOUT_E_MAX)
            Rank.D -> rng.nextIntInclusive(BalanceSettings.PAYOUT_D_MIN, BalanceSettings.PAYOUT_D_MAX)
            Rank.C -> rng.nextIntInclusive(BalanceSettings.PAYOUT_C_MIN, BalanceSettings.PAYOUT_C_MAX)
            Rank.B -> rng.nextIntInclusive(BalanceSettings.PAYOUT_B_MIN, BalanceSettings.PAYOUT_B_MAX)
            Rank.A -> sampleAWithTail(rng)
            Rank.S -> rng.nextIntInclusive(BalanceSettings.PAYOUT_S_MIN, BalanceSettings.PAYOUT_S_MAX)
        }
    }

    /**
     * Sample the client's deposit contribution (gp).
     *
     * Hardcoded MVP rule:
     * - with probability CLIENT_PAYS_CHANCE_PERCENT: deposit = payout * CLIENT_PAYS_FRACTION_BP / 10000
     * - otherwise: deposit = 0
     *
     * @deprecated Use sampleClientDepositMoney() instead (FP-ECON-02). This method
     *   uses integer division which truncates small deposits to 0.
     * @param rank Target contract rank.
     * @param rng Deterministic RNG.
     * @return Client deposit in GP (>= 0).
     */
    @Deprecated(
        message = "Use sampleClientDepositMoney() for copper-based calculations",
        replaceWith = ReplaceWith("sampleClientDepositMoney(samplePayoutMoney(rank, rng), rng)")
    )
    fun sampleClientDepositGp(rank: Rank, rng: Rng): Int {
        val payout = samplePayoutGp(rank, rng)
        val roll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
        if (roll >= BalanceSettings.CLIENT_PAYS_CHANCE_PERCENT) return 0

        val deposit = (payout * BalanceSettings.CLIENT_PAYS_FRACTION_BP) / 10_000
        return deposit.coerceAtLeast(0)
    }

    /**
     * Sample Rank A with heavy-tail distribution.
     *
     * Most of the time samples from base range [A_MIN, A_MAX].
     * With PAYOUT_A_TAIL_CHANCE_PERCENT probability, samples from tail range [A_TAIL_MIN, A_TAIL_MAX].
     */
    private fun sampleAWithTail(rng: Rng): Int {
        val roll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
        return if (roll < BalanceSettings.PAYOUT_A_TAIL_CHANCE_PERCENT) {
            rng.nextIntInclusive(BalanceSettings.PAYOUT_A_TAIL_MIN, BalanceSettings.PAYOUT_A_TAIL_MAX)
        } else {
            rng.nextIntInclusive(BalanceSettings.PAYOUT_A_MIN, BalanceSettings.PAYOUT_A_MAX)
        }
    }
}

/**
 * Inclusive Int range sampling using nextInt.
 *
 * @param min Minimum value (inclusive).
 * @param max Maximum value (inclusive).
 * @return Random value in [min, max].
 */
private fun Rng.nextIntInclusive(min: Int, max: Int): Int {
    require(max >= min) { "max ($max) must be >= min ($min)" }
    val span = (max - min) + 1
    return min + this.nextInt(span)
}
