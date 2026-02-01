// FILE: core/src/main/kotlin/core/primitives/Money.kt
package core.primitives

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Money value stored in copper units (1 gp = 100 copper, 1 silver = 10 copper).
 *
 * ## Contract
 * - Copper value must always be >= 0
 * - Immutable value type
 * - Deterministic: same inputs → same outputs across all JVMs
 *
 * ## Stability Gradient
 * STABLE: Core money primitive used throughout economy calculations.
 */
@JvmInline
value class MoneyCopper(val copper: Int) {
    init {
        require(copper >= 0) { "Money cannot be negative: $copper copper" }
    }

    operator fun plus(other: MoneyCopper): MoneyCopper = MoneyCopper(copper + other.copper)

    operator fun compareTo(other: MoneyCopper): Int = copper.compareTo(other.copper)

    override fun toString(): String = "$copper copper"
}

/**
 * Money conversion and calculation API.
 *
 * ## Role
 * - Single source of truth for all money unit conversions
 * - Enforces floor-rounding semantics for all decimal → copper conversions
 * - Provides fraction/percentage calculations in copper units
 *
 * ## Contract
 * - All conversions from decimal to copper use floor (round down)
 * - All fraction calculations preserve determinism (no floating-point errors)
 * - Basis points (bp) are in range [0..10000] where 10000 = 100%
 *
 * ## Determinism
 * - Uses BigDecimal with explicit scale and rounding mode
 * - Same inputs always produce same outputs across JVM instances
 */
object Money {
    /** Copper per gold piece */
    const val COPPER_PER_GP = 100

    /** Copper per silver piece */
    const val COPPER_PER_SILVER = 10

    /** Basis points divisor (10000 = 100%) */
    const val BP_DIVISOR = 10_000

    /** Zero money constant */
    val ZERO = MoneyCopper(0)

    /**
     * Create money from copper value.
     *
     * @param copper Amount in copper (must be >= 0)
     * @return MoneyCopper instance
     */
    fun fromCopper(copper: Int): MoneyCopper {
        require(copper >= 0) { "Copper must be non-negative: $copper" }
        return MoneyCopper(copper)
    }

    /**
     * Create money from gold pieces (decimal).
     * Rounds down (floor) to nearest copper.
     *
     * @param gp Amount in gold pieces
     * @return MoneyCopper instance (floored to copper)
     */
    fun fromGoldDecimal(gp: BigDecimal): MoneyCopper {
        val copper = gp.multiply(BigDecimal(COPPER_PER_GP))
            .setScale(0, RoundingMode.DOWN)
            .toInt()
        return MoneyCopper(copper.coerceAtLeast(0))
    }

    /**
     * Create money from silver pieces (decimal).
     * Rounds down (floor) to nearest copper.
     *
     * @param silver Amount in silver pieces
     * @return MoneyCopper instance (floored to copper)
     */
    fun fromSilverDecimal(silver: BigDecimal): MoneyCopper {
        val copper = silver.multiply(BigDecimal(COPPER_PER_SILVER))
            .setScale(0, RoundingMode.DOWN)
            .toInt()
        return MoneyCopper(copper.coerceAtLeast(0))
    }

    /**
     * Convert money to gold pieces as decimal (for display/tests).
     *
     * @param money Money in copper
     * @return Amount in gold pieces (exact decimal)
     */
    fun toGoldDecimal(money: MoneyCopper): BigDecimal {
        return BigDecimal(money.copper).divide(BigDecimal(COPPER_PER_GP), 2, RoundingMode.HALF_UP)
    }

    /**
     * Multiply money by a fraction expressed in basis points.
     * Result is floored to copper.
     *
     * Formula: result = floor(copper * bp / 10000)
     *
     * @param money Money to multiply
     * @param bp Basis points (0..10000, where 10000 = 100%)
     * @return Result money (floored)
     */
    fun mulFractionBp(money: MoneyCopper, bp: Int): MoneyCopper {
        require(bp in 0..BP_DIVISOR) { "Basis points must be in [0, $BP_DIVISOR]: $bp" }
        val result = (money.copper.toLong() * bp) / BP_DIVISOR.toLong()
        return MoneyCopper(result.toInt())
    }

    /**
     * Add two money amounts.
     *
     * @param a First amount
     * @param b Second amount
     * @return Sum
     */
    fun plus(a: MoneyCopper, b: MoneyCopper): MoneyCopper {
        return MoneyCopper(a.copper + b.copper)
    }

    /**
     * Subtract money amounts, ensuring result is non-negative.
     * If b > a, returns ZERO.
     *
     * @param a Amount to subtract from
     * @param b Amount to subtract
     * @return Result (clamped to >= 0)
     */
    fun minusNonNegative(a: MoneyCopper, b: MoneyCopper): MoneyCopper {
        val result = a.copper - b.copper
        return MoneyCopper(result.coerceAtLeast(0))
    }

    /**
     * Return minimum of two money amounts.
     */
    fun min(a: MoneyCopper, b: MoneyCopper): MoneyCopper {
        return if (a.copper <= b.copper) a else b
    }

    /**
     * Return maximum of two money amounts.
     */
    fun max(a: MoneyCopper, b: MoneyCopper): MoneyCopper {
        return if (a.copper >= b.copper) a else b
    }
}
