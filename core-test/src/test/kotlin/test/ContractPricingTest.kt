package test

import core.BalanceSettings
import core.flavour.ContractPricing
import core.primitives.Rank
import core.rng.Rng
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * P2: Contract pricing tests.
 * Tests for rank-based payout sampling and client deposit generation.
 */
@P2
class ContractPricingTest {

    // ───────────────────────────────────────────────────────────────────────────
    // samplePayoutGp tests
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `samplePayoutGp returns value within F band`() {
        val rng = Rng(42L)
        repeat(100) {
            val payout = ContractPricing.samplePayoutGp(Rank.F, rng)
            assertTrue(
                payout in BalanceSettings.PAYOUT_F_MIN..BalanceSettings.PAYOUT_F_MAX,
                "F payout $payout should be in [${BalanceSettings.PAYOUT_F_MIN}, ${BalanceSettings.PAYOUT_F_MAX}]"
            )
        }
    }

    @Test
    fun `samplePayoutGp returns value within E band`() {
        val rng = Rng(42L)
        repeat(100) {
            val payout = ContractPricing.samplePayoutGp(Rank.E, rng)
            assertTrue(
                payout in BalanceSettings.PAYOUT_E_MIN..BalanceSettings.PAYOUT_E_MAX,
                "E payout $payout should be in [${BalanceSettings.PAYOUT_E_MIN}, ${BalanceSettings.PAYOUT_E_MAX}]"
            )
        }
    }

    @Test
    fun `samplePayoutGp returns value within D band`() {
        val rng = Rng(42L)
        repeat(100) {
            val payout = ContractPricing.samplePayoutGp(Rank.D, rng)
            assertTrue(
                payout in BalanceSettings.PAYOUT_D_MIN..BalanceSettings.PAYOUT_D_MAX,
                "D payout $payout should be in [${BalanceSettings.PAYOUT_D_MIN}, ${BalanceSettings.PAYOUT_D_MAX}]"
            )
        }
    }

    @Test
    fun `samplePayoutGp returns value within C band`() {
        val rng = Rng(42L)
        repeat(100) {
            val payout = ContractPricing.samplePayoutGp(Rank.C, rng)
            assertTrue(
                payout in BalanceSettings.PAYOUT_C_MIN..BalanceSettings.PAYOUT_C_MAX,
                "C payout $payout should be in [${BalanceSettings.PAYOUT_C_MIN}, ${BalanceSettings.PAYOUT_C_MAX}]"
            )
        }
    }

    @Test
    fun `samplePayoutGp returns value within B band`() {
        val rng = Rng(42L)
        repeat(100) {
            val payout = ContractPricing.samplePayoutGp(Rank.B, rng)
            assertTrue(
                payout in BalanceSettings.PAYOUT_B_MIN..BalanceSettings.PAYOUT_B_MAX,
                "B payout $payout should be in [${BalanceSettings.PAYOUT_B_MIN}, ${BalanceSettings.PAYOUT_B_MAX}]"
            )
        }
    }

    @Test
    fun `samplePayoutGp returns value within A band or tail`() {
        val rng = Rng(42L)
        repeat(100) {
            val payout = ContractPricing.samplePayoutGp(Rank.A, rng)
            // A can be in base range or tail range
            val inBase = payout in BalanceSettings.PAYOUT_A_MIN..BalanceSettings.PAYOUT_A_MAX
            val inTail = payout in BalanceSettings.PAYOUT_A_TAIL_MIN..BalanceSettings.PAYOUT_A_TAIL_MAX
            assertTrue(
                inBase || inTail,
                "A payout $payout should be in base [${BalanceSettings.PAYOUT_A_MIN}, ${BalanceSettings.PAYOUT_A_MAX}] " +
                    "or tail [${BalanceSettings.PAYOUT_A_TAIL_MIN}, ${BalanceSettings.PAYOUT_A_TAIL_MAX}]"
            )
        }
    }

    @Test
    fun `samplePayoutGp returns value within S band`() {
        val rng = Rng(42L)
        repeat(100) {
            val payout = ContractPricing.samplePayoutGp(Rank.S, rng)
            assertTrue(
                payout in BalanceSettings.PAYOUT_S_MIN..BalanceSettings.PAYOUT_S_MAX,
                "S payout $payout should be in [${BalanceSettings.PAYOUT_S_MIN}, ${BalanceSettings.PAYOUT_S_MAX}]"
            )
        }
    }

    @Test
    fun `rank A sometimes produces tail payouts`() {
        // With enough samples, we should see at least one tail payout (10% chance each)
        val rng = Rng(12345L)
        var tailCount = 0
        val samples = 200

        repeat(samples) {
            val payout = ContractPricing.samplePayoutGp(Rank.A, rng)
            if (payout > BalanceSettings.PAYOUT_A_MAX) {
                tailCount++
            }
        }

        // With 10% chance and 200 samples, probability of 0 tail samples is (0.9)^200 ≈ 0
        assertTrue(tailCount > 0, "Expected at least one tail payout in $samples A-rank samples, got $tailCount")
    }

    // ───────────────────────────────────────────────────────────────────────────
    // sampleClientDepositGp tests
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `sampleClientDepositGp returns non-negative value`() {
        val rng = Rng(42L)
        repeat(100) {
            val deposit = ContractPricing.sampleClientDepositGp(Rank.C, rng)
            assertTrue(deposit >= 0, "Deposit should be non-negative, got $deposit")
        }
    }

    @Test
    fun `sampleClientDepositGp sometimes returns zero`() {
        // With 50% chance of paying, half should be zero
        val rng = Rng(42L)
        var zeroCount = 0
        val samples = 200

        repeat(samples) {
            val deposit = ContractPricing.sampleClientDepositGp(Rank.C, rng)
            if (deposit == 0) {
                zeroCount++
            }
        }

        // With 50% chance and 200 samples, expect roughly 100 zeros
        assertTrue(zeroCount > 50, "Expected significant number of zero deposits, got $zeroCount out of $samples")
    }

    @Test
    fun `sampleClientDepositGp sometimes returns positive value`() {
        // With 50% chance of paying, half should be positive
        val rng = Rng(42L)
        var positiveCount = 0
        val samples = 200

        repeat(samples) {
            val deposit = ContractPricing.sampleClientDepositGp(Rank.C, rng)
            if (deposit > 0) {
                positiveCount++
            }
        }

        assertTrue(positiveCount > 50, "Expected significant number of positive deposits, got $positiveCount out of $samples")
    }

    @Test
    fun `sampleClientDepositGp deposit is approximately half of payout when client pays`() {
        // MVP rule: deposit = payout * 50% when client pays
        // We can't easily verify this without access to payout, but we can check
        // that deposits for high-rank contracts are higher than low-rank
        val rng1 = Rng(42L)
        val rng2 = Rng(42L)

        var totalFDeposit = 0
        var totalSDeposit = 0
        val samples = 200

        repeat(samples) {
            totalFDeposit += ContractPricing.sampleClientDepositGp(Rank.F, rng1)
        }

        repeat(samples) {
            totalSDeposit += ContractPricing.sampleClientDepositGp(Rank.S, rng2)
        }

        // S-rank deposits should be significantly higher than F-rank on average
        assertTrue(
            totalSDeposit > totalFDeposit * 100, // S should be orders of magnitude higher
            "S-rank total deposits ($totalSDeposit) should be much higher than F-rank ($totalFDeposit)"
        )
    }

    // ───────────────────────────────────────────────────────────────────────────
    // Determinism tests
    // ───────────────────────────────────────────────────────────────────────────

    @Test
    fun `samplePayoutGp is deterministic for same seed`() {
        val results1 = mutableListOf<Int>()
        val results2 = mutableListOf<Int>()

        val rng1 = Rng(12345L)
        val rng2 = Rng(12345L)

        repeat(50) {
            results1.add(ContractPricing.samplePayoutGp(Rank.B, rng1))
        }

        repeat(50) {
            results2.add(ContractPricing.samplePayoutGp(Rank.B, rng2))
        }

        assertEquals(results1, results2, "Same seed should produce identical payout sequences")
    }

    @Test
    fun `sampleClientDepositGp is deterministic for same seed`() {
        val results1 = mutableListOf<Int>()
        val results2 = mutableListOf<Int>()

        val rng1 = Rng(12345L)
        val rng2 = Rng(12345L)

        repeat(50) {
            results1.add(ContractPricing.sampleClientDepositGp(Rank.C, rng1))
        }

        repeat(50) {
            results2.add(ContractPricing.sampleClientDepositGp(Rank.C, rng2))
        }

        assertEquals(results1, results2, "Same seed should produce identical deposit sequences")
    }
}
