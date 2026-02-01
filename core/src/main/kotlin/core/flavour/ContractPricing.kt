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
 * - Converts target contract rank into (a) sampled payout (money) and
 *   (b) clientDeposit contribution.
 *
 * ## Money Contract (FP-ECON-02)
 * - Canonical storage unit across core is **copper**.
 * - `BalanceSettings.PAYOUT_*` ranges are expressed in **GP** (design bands).
 * - This pricing component samples from GP bands, then converts to
 *   [MoneyCopper] (1 gp = 100 copper).
 * - Any decimal → copper conversion uses **floor** rounding as implemented
 *   in [Money].
 *
 * ## Determinism
 * - Uses explicit RNG draws in a fixed order.
 * - Same seed + same rank → identical results.
 * - Do not reorder draws without updating determinism tests.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object ContractPricing {

    /**
     * Sample the payout value for a contract in copper units.
     *
     * ## Inputs
     * - [rank] selects the GP payout band from [BalanceSettings].
     * - [rng] provides deterministic draws.
     *
     * @param rank Target contract rank.
     * @param rng Deterministic RNG.
     *
     * ## Output
     * @return Sampled payout as [MoneyCopper] (>= 0).
     *
     * ## Notes
     * - Sampling is uniform over the configured GP band.
     * - Conversion: `gp * 100` copper (via [Money.fromGoldDecimal]).
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
     * ## Behavior
     * - Performs a percent roll (`CLIENT_PAYS_CHANCE_PERCENT`).
     * - If client pays: deposit = floor(payout * CLIENT_PAYS_FRACTION_BP / 10_000)
     *   computed in copper via [Money.mulFractionBp].
     * - Otherwise: deposit = 0.
     *
     * @param payout Sampled payout in copper.
     * @param rng Deterministic RNG.
     * @return Client deposit in copper.
     *
     * ## Invariants
     * - deposit >= 0
     * - deposit <= payout
     */
    fun sampleClientDepositMoney(payout: MoneyCopper, rng: Rng): MoneyCopper {
        val roll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
        if (roll >= BalanceSettings.CLIENT_PAYS_CHANCE_PERCENT) return Money.ZERO

        // Calculate deposit as fraction of payout in copper (no truncation)
        return Money.mulFractionBp(payout, BalanceSettings.CLIENT_PAYS_FRACTION_BP)
    }

    /**
     * Legacy API: sample payout in GP as an Int.
     *
     * @param rank Target contract rank.
     * @param rng Deterministic RNG.
     * @return Sampled payout in GP.
     *
     * @deprecated FP-ECON-02: Use [samplePayoutMoney] for copper-safe logic.
     * This method returns GP as Int and should be avoided in domain logic.
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
     * Legacy API: sample deposit in GP as an Int.
     *
     * @param rank Target contract rank.
     * @param rng Deterministic RNG.
     * @return Client deposit in GP.
     *
     * @deprecated FP-ECON-02: Use [sampleClientDepositMoney] for copper-safe
     * deposit logic. This method uses integer division in GP space and may
     * truncate small values to 0.
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
     *
     * @param rng Deterministic RNG.
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
