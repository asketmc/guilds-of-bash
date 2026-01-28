// FILE: core/src/main/kotlin/core/BalanceSettings.kt
package core

/**
 * Balance settings for outcome resolution and other gameplay parameters.
 *
 * ## Contract
 * All values are immutable constants. Changing them affects game balance deterministically.
 *
 * ## Invariants
 * - SUCCESS_CHANCE_MIN <= SUCCESS_CHANCE_MAX
 * - SUCCESS_CHANCE_MIN >= 0, SUCCESS_CHANCE_MAX <= 100
 * - FAIL_CHANCE_MIN >= 1 (FAIL must always be reachable)
 * - pSuccess + pPartial + pFail = 100 for any valid input
 *
 * ## Determinism
 * All calculations using these constants are deterministic.
 */
object BalanceSettings {
    // ─────────────────────────────────────────────────────────────────────────
    // Outcome Resolution Parameters
    // ─────────────────────────────────────────────────────────────────────────

    /** Minimum success chance percentage (floor). */
    const val SUCCESS_CHANCE_MIN: Int = 5

    /** Maximum success chance percentage (ceiling). */
    const val SUCCESS_CHANCE_MAX: Int = 85

    /** Offset added to (heroPower - difficulty) before multiplying. */
    const val SUCCESS_FORMULA_OFFSET: Int = 5

    /** Multiplier for (heroPower - difficulty + offset). */
    const val SUCCESS_FORMULA_MULTIPLIER: Int = 20

    /** Fixed partial outcome chance percentage. */
    const val PARTIAL_CHANCE_FIXED: Int = 14

    /** Minimum fail chance percentage (FAIL must always be reachable). */
    const val FAIL_CHANCE_MIN: Int = 1

    // ─────────────────────────────────────────────────────────────────────────
    // Derived Constraints (compile-time assertions via init block)
    // ─────────────────────────────────────────────────────────────────────────

    init {
        require(SUCCESS_CHANCE_MIN >= 0) { "SUCCESS_CHANCE_MIN must be >= 0" }
        require(SUCCESS_CHANCE_MAX <= 100) { "SUCCESS_CHANCE_MAX must be <= 100" }
        require(SUCCESS_CHANCE_MIN <= SUCCESS_CHANCE_MAX) { "SUCCESS_CHANCE_MIN must be <= SUCCESS_CHANCE_MAX" }
        require(FAIL_CHANCE_MIN >= 1) { "FAIL_CHANCE_MIN must be >= 1 (FAIL must always be reachable)" }

        // Ensure normalized probabilities: pSuccess_max + pPartial + pFail_min <= 100
        val maxPossibleNonFail = SUCCESS_CHANCE_MAX + PARTIAL_CHANCE_FIXED
        require(maxPossibleNonFail <= 100 - FAIL_CHANCE_MIN) {
            "SUCCESS_CHANCE_MAX ($SUCCESS_CHANCE_MAX) + PARTIAL_CHANCE_FIXED ($PARTIAL_CHANCE_FIXED) " +
                "must leave room for FAIL_CHANCE_MIN ($FAIL_CHANCE_MIN): max non-fail = $maxPossibleNonFail, " +
                "but 100 - FAIL_CHANCE_MIN = ${100 - FAIL_CHANCE_MIN}"
        }
    }
}
