// FILE: core/src/main/kotlin/core/primitives/Money.kt
package core.primitives

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money value stored in **copper** units.
 *
 * ## Units
 * - Storage unit: **copper** (Int)
 * - 1 gp = 100 copper
 * - 1 silver = 10 copper
 *
 * ## Contract
 * - [copper] is always **>= 0**.
 * - Immutable value type.
 * - Intended as the canonical in-core representation at API boundaries
 *   (pricing, escrow, tax, settlements).
 *
 * ## Determinism
 * Pure value wrapper; determinism depends on callers. All computations should
 * be performed via [Money] helpers to preserve consistent floor-rounding
 * semantics.
 */
@JvmInline
value class MoneyCopper(
    /** Amount in copper units (>= 0). */
    val copper: Int
) {
    init {
        require(copper >= 0) { "Money cannot be negative: $copper copper" }
    }

    /**
     * Add copper amounts.
     *
     * @param other Addend.
     */
    operator fun plus(other: MoneyCopper): MoneyCopper = MoneyCopper(copper + other.copper)

    /**
     * Compare copper amounts.
     *
     * @param other Comparator value.
     */
    operator fun compareTo(other: MoneyCopper): Int = copper.compareTo(other.copper)

    /** Human-readable debug string (unit: copper). */
    override fun toString(): String = "$copper copper"
}

/**
 * FP-ECON-02 Money Contract: conversion & arithmetic utilities.
 *
 * ## Role
 * Single source of truth for:
 * - Unit conversion (gp/silver ↔ copper)
 * - Basis-point fraction math in copper
 * - Centralized rounding semantics (floor) when converting decimal → copper
 *
 * ## Normative Semantics
 * - Any conversion from a decimal amount (gp/silver) to copper uses
 *   **floor** (round down) to avoid creating money from rounding.
 * - Any percentage/fraction calculation runs in integer space and implicitly
 *   floors (see [mulFractionBp]).
 *
 * ## Determinism
 * - No floating-point math.
 * - Uses [BigDecimal] only with explicit scale and [RoundingMode]
 *   to keep results stable across JVMs.
 */
object Money {
    /** Copper per gold piece (gp). */
    const val COPPER_PER_GP = 100

    /** Copper per silver piece. */
    const val COPPER_PER_SILVER = 10

    /** Basis points divisor (10_000 = 100%). */
    const val BP_DIVISOR = 10_000

    /** Zero money constant. */
    val ZERO = MoneyCopper(0)

    /**
     * Create from a raw copper value.
     *
     * @param copper Amount in copper (must be >= 0).
     */
    fun fromCopper(copper: Int): MoneyCopper {
        require(copper >= 0) { "Copper must be non-negative: $copper" }
        return MoneyCopper(copper)
    }

    /**
     * Convert a gold-piece decimal amount into copper.
     *
     * Rounding: **floor** (round down) to the nearest copper.
     *
     * @param gp Amount in gold pieces (decimal).
     * @return Equivalent amount in copper (floored).
     */
    fun fromGoldDecimal(gp: BigDecimal): MoneyCopper {
        val copper = gp.multiply(BigDecimal(COPPER_PER_GP))
            .setScale(0, RoundingMode.DOWN)
            .toInt()
        return MoneyCopper(copper.coerceAtLeast(0))
    }

    /**
     * Convert a silver-piece decimal amount into copper.
     *
     * Rounding: **floor** (round down) to the nearest copper.
     *
     * @param silver Amount in silver pieces (decimal).
     * @return Equivalent amount in copper (floored).
     */
    fun fromSilverDecimal(silver: BigDecimal): MoneyCopper {
        val copper = silver.multiply(BigDecimal(COPPER_PER_SILVER))
            .setScale(0, RoundingMode.DOWN)
            .toInt()
        return MoneyCopper(copper.coerceAtLeast(0))
    }

    /**
     * Convert copper into a gold-piece decimal representation.
     *
     * Intended for display and tests; state storage remains copper.
     *
     * @param money Money in copper.
     */
    fun toGoldDecimal(money: MoneyCopper): BigDecimal {
        return BigDecimal(money.copper).divide(BigDecimal(COPPER_PER_GP), 2, RoundingMode.HALF_UP)
    }

    /**
     * Multiply a copper amount by a basis-point fraction.
     *
     * Formula: `floor(copper * bp / 10_000)`
     *
     * This is the canonical helper for deposits, taxes, penalties, etc.
     *
     * @param money Money amount to multiply.
     * @param bp Basis points in [0..10_000].
     */
    fun mulFractionBp(money: MoneyCopper, bp: Int): MoneyCopper {
        require(bp in 0..BP_DIVISOR) { "Basis points must be in [0, $BP_DIVISOR]: $bp" }
        val result = (money.copper.toLong() * bp) / BP_DIVISOR.toLong()
        return MoneyCopper(result.toInt())
    }

    /**
     * Add two money amounts.
     *
     * @param a First amount.
     * @param b Second amount.
     */
    fun plus(a: MoneyCopper, b: MoneyCopper): MoneyCopper {
        return MoneyCopper(a.copper + b.copper)
    }

    /**
     * Subtract money amounts and clamp at zero.
     *
     * @param a Minuend.
     * @param b Subtrahend.
     */
    fun minusNonNegative(a: MoneyCopper, b: MoneyCopper): MoneyCopper {
        val result = a.copper - b.copper
        return MoneyCopper(result.coerceAtLeast(0))
    }

    /**
     * Minimum of two money amounts.
     *
     * @param a First amount.
     * @param b Second amount.
     */
    fun min(a: MoneyCopper, b: MoneyCopper): MoneyCopper {
        return if (a.copper <= b.copper) a else b
    }

    /**
     * Maximum of two money amounts.
     *
     * @param a First amount.
     * @param b Second amount.
     */
    fun max(a: MoneyCopper, b: MoneyCopper): MoneyCopper {
        return if (a.copper >= b.copper) a else b
    }
}
