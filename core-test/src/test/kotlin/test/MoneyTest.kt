// FILE: core-test/src/test/kotlin/test/MoneyTest.kt
package test

import core.primitives.Money
import core.primitives.MoneyCopper
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

/**
 * Unit tests for Money conversion and calculation API.
 *
 * Tests the FP-ECON-02 Money Contract:
 * - Floor rounding for all decimal → copper conversions
 * - Deterministic fraction calculations
 * - Non-negative invariants
 */
class MoneyTest {

    // ─────────────────────────────────────────────────────────────────────────────
    // Conversion Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `fromCopper creates valid money`() {
        val money = Money.fromCopper(100)
        assertEquals(100, money.copper)
    }

    @Test
    fun `fromCopper rejects negative`() {
        assertThrows<IllegalArgumentException> {
            Money.fromCopper(-1)
        }
    }

    @Test
    fun `fromGoldDecimal floors to copper`() {
        // 0.009 gp = 0.9 copper → floors to 0
        assertEquals(0, Money.fromGoldDecimal(BigDecimal("0.009")).copper)

        // 0.010 gp = 1.0 copper → floors to 1
        assertEquals(1, Money.fromGoldDecimal(BigDecimal("0.010")).copper)

        // 1.000 gp = 100.0 copper → floors to 100
        assertEquals(100, Money.fromGoldDecimal(BigDecimal("1.000")).copper)

        // 1.999 gp = 199.9 copper → floors to 199
        assertEquals(199, Money.fromGoldDecimal(BigDecimal("1.999")).copper)
    }

    @Test
    fun `fromGoldDecimal handles F-tier range`() {
        // PAYOUT_F_MIN = 0 gp
        assertEquals(0, Money.fromGoldDecimal(BigDecimal("0.00")).copper)

        // PAYOUT_F_MAX = 1 gp
        assertEquals(100, Money.fromGoldDecimal(BigDecimal("1.00")).copper)

        // Mid-range F-tier: 0.5 gp = 50 copper
        assertEquals(50, Money.fromGoldDecimal(BigDecimal("0.50")).copper)
    }

    @Test
    fun `fromSilverDecimal floors to copper`() {
        // 0.09 silver = 0.9 copper → floors to 0
        assertEquals(0, Money.fromSilverDecimal(BigDecimal("0.09")).copper)

        // 0.10 silver = 1.0 copper → floors to 1
        assertEquals(1, Money.fromSilverDecimal(BigDecimal("0.10")).copper)

        // 1.00 silver = 10.0 copper → floors to 10
        assertEquals(10, Money.fromSilverDecimal(BigDecimal("1.00")).copper)
    }

    @Test
    fun `toGoldDecimal converts copper to gp`() {
        assertEquals(BigDecimal("0.00"), Money.toGoldDecimal(MoneyCopper(0)))
        assertEquals(BigDecimal("0.01"), Money.toGoldDecimal(MoneyCopper(1)))
        assertEquals(BigDecimal("0.50"), Money.toGoldDecimal(MoneyCopper(50)))
        assertEquals(BigDecimal("1.00"), Money.toGoldDecimal(MoneyCopper(100)))
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Fraction Calculation Tests (Basis Points)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `mulFractionBp calculates 50 percent correctly`() {
        // 100 copper * 50% (5000 bp) = 50 copper
        assertEquals(50, Money.mulFractionBp(MoneyCopper(100), 5000).copper)

        // 1 gp (100 copper) * 50% = 50 copper (NOT 0!)
        val oneGold = Money.fromGoldDecimal(BigDecimal("1.00"))
        assertEquals(50, Money.mulFractionBp(oneGold, 5000).copper)
    }

    @Test
    fun `mulFractionBp floors small fractions`() {
        // 1 copper * 50% (5000 bp) = 0.5 copper → floors to 0
        assertEquals(0, Money.mulFractionBp(MoneyCopper(1), 5000).copper)

        // 3 copper * 50% = 1.5 copper → floors to 1
        assertEquals(1, Money.mulFractionBp(MoneyCopper(3), 5000).copper)
    }

    @Test
    fun `mulFractionBp handles edge cases`() {
        // 0% of anything is 0
        assertEquals(0, Money.mulFractionBp(MoneyCopper(100), 0).copper)

        // 100% (10000 bp) of X is X
        assertEquals(100, Money.mulFractionBp(MoneyCopper(100), 10_000).copper)

        // 1% (100 bp) of 100 copper = 1 copper
        assertEquals(1, Money.mulFractionBp(MoneyCopper(100), 100).copper)
    }

    @Test
    fun `mulFractionBp rejects invalid basis points`() {
        assertThrows<IllegalArgumentException> {
            Money.mulFractionBp(MoneyCopper(100), -1)
        }

        assertThrows<IllegalArgumentException> {
            Money.mulFractionBp(MoneyCopper(100), 10_001)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Arithmetic Tests
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `plus adds money correctly`() {
        val a = MoneyCopper(50)
        val b = MoneyCopper(30)
        assertEquals(80, Money.plus(a, b).copper)
    }

    @Test
    fun `minusNonNegative subtracts correctly`() {
        val a = MoneyCopper(100)
        val b = MoneyCopper(30)
        assertEquals(70, Money.minusNonNegative(a, b).copper)
    }

    @Test
    fun `minusNonNegative clamps to zero when b greater than a`() {
        val a = MoneyCopper(30)
        val b = MoneyCopper(100)
        assertEquals(0, Money.minusNonNegative(a, b).copper)
    }

    @Test
    fun `min returns smaller amount`() {
        assertEquals(30, Money.min(MoneyCopper(30), MoneyCopper(100)).copper)
        assertEquals(30, Money.min(MoneyCopper(100), MoneyCopper(30)).copper)
    }

    @Test
    fun `max returns larger amount`() {
        assertEquals(100, Money.max(MoneyCopper(30), MoneyCopper(100)).copper)
        assertEquals(100, Money.max(MoneyCopper(100), MoneyCopper(30)).copper)
    }

    // ─────────────────────────────────────────────────────────────────────────────
    // Real-World Scenario Tests (Reproducing the Bug)
    // ─────────────────────────────────────────────────────────────────────────────

    @Test
    fun `F-tier payout 1 gp with 50 percent client deposit produces 50 copper deposit`() {
        // This is the bug we're fixing:
        // OLD: payout=1 GP (int), deposit = (1 * 5000) / 10000 = 0 (integer division)
        // NEW: payout=100 copper, deposit = (100 * 5000) / 10000 = 50 copper

        val payoutGp = BigDecimal("1.00")
        val payout = Money.fromGoldDecimal(payoutGp)
        assertEquals(100, payout.copper, "1 gp should be 100 copper")

        val deposit = Money.mulFractionBp(payout, 5000) // 50%
        assertEquals(50, deposit.copper, "50% of 100 copper should be 50 copper, not 0")
    }

    @Test
    fun `F-tier payout range 0-1 gp maps to 0-100 copper`() {
        val minPayout = Money.fromGoldDecimal(BigDecimal("0.00"))
        val maxPayout = Money.fromGoldDecimal(BigDecimal("1.00"))

        assertEquals(0, minPayout.copper)
        assertEquals(100, maxPayout.copper)
    }

    @Test
    fun `client deposit never exceeds payout`() {
        val payout = MoneyCopper(100)

        // Even at 100% fraction, deposit should equal payout
        val deposit100 = Money.mulFractionBp(payout, 10_000)
        assertEquals(100, deposit100.copper)
        assert(deposit100.copper <= payout.copper)

        // At 50%, deposit should be half
        val deposit50 = Money.mulFractionBp(payout, 5_000)
        assertEquals(50, deposit50.copper)
        assert(deposit50.copper <= payout.copper)
    }
}
