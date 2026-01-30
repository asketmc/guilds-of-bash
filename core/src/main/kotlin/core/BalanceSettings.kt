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

    /**
     * Upper bound (exclusive) for percentage-based RNG rolls.
     *
     * Most reducer rolls are modeled as `rng.nextInt(PERCENT_ROLL_MAX)` and compared against chance
     * values expressed in percent.
     *
     * Typical value: `100` (meaning roll in range `[0, 100)`).
     * Must be > 0.
     */
    const val PERCENT_ROLL_MAX: Int = 100

    /**
     * Chance (percent) that a DEATH resolution is reported as MISSING instead, for narrative variety.
     * PoC/MVP: small fixed chance; kept deterministic via RNG seed. Value in [0,100).
     */
    const val MISSING_CHANCE_PERCENT: Int = 10

    // ─────────────────────────────────────────────────────────────────────────
    // Gameplay Timing & Economic Parameters
    // ─────────────────────────────────────────────────────────────────────────

    /** Default number of drafts generated per inbox cycle (rank F). */
    const val DEFAULT_N_INBOX: Int = 2

    /** Default number of new heroes arriving per day (rank F). */
    const val DEFAULT_N_HEROES: Int = 2

    /** Initial days remaining when a hero accepts a contract. */
    const val DAYS_REMAINING_INIT: Int = 2

    /** Interval in days between tax assessments. */
    const val TAX_INTERVAL_DAYS: Int = 7

    /** Base copper amount charged every tax interval. */
    const val TAX_BASE_AMOUNT: Int = 10

    /** Percentage penalty added to tax when a payment is missed. */
    const val TAX_PENALTY_PERCENT: Int = 10

    /** Maximum number of missed taxes before suffering a shutdown. */
    const val TAX_MAX_MISSED: Int = 3

    /** Number of days until auto-resolve runs for a stale draft. */
    const val AUTO_RESOLVE_INTERVAL_DAYS: Int = 7

    /** Threshold under which heroes will automatically decline risky quests. */
    const val DECLINE_HARD_THRESHOLD: Int = -30

    /** Stability penalty applied when a BAD auto-resolve occurs (per Miss). */
    const val STABILITY_PENALTY_BAD_AUTO_RESOLVE: Int = 2

    /** Minimum stability value (floor). */
    const val STABILITY_MIN: Int = 0

    /** Maximum stability value (ceiling). */
    const val STABILITY_MAX: Int = 100

    /** Base multiplier for inbox/heroes per rank level. */
    const val RANK_MULTIPLIER_BASE: Int = 2

    /** Default contract difficulty when board contract is missing. */
    const val DEFAULT_CONTRACT_DIFFICULTY: Int = 1

    // ─────────────────────────────────────────────────────────────────────────
    // Derived Constraints (compile-time assertions via init block)
    // ─────────────────────────────────────────────────────────────────────────

    init {
        require(SUCCESS_CHANCE_MIN >= 0) { "SUCCESS_CHANCE_MIN must be >= 0" }
        require(SUCCESS_CHANCE_MAX <= 100) { "SUCCESS_CHANCE_MAX must be <= 100" }
        require(SUCCESS_CHANCE_MIN <= SUCCESS_CHANCE_MAX) { "SUCCESS_CHANCE_MIN must be <= SUCCESS_CHANCE_MAX" }
        require(FAIL_CHANCE_MIN >= 1) { "FAIL_CHANCE_MIN must be >= 1 (FAIL must always be reachable)" }
        require(PERCENT_ROLL_MAX > 0) { "PERCENT_ROLL_MAX must be > 0" }

        // Ensure normalized probabilities: pSuccess_max + pPartial + pFail_min <= 100
        val maxPossibleNonFail = SUCCESS_CHANCE_MAX + PARTIAL_CHANCE_FIXED
        require(maxPossibleNonFail <= 100 - FAIL_CHANCE_MIN) {
            "SUCCESS_CHANCE_MAX ($SUCCESS_CHANCE_MAX) + PARTIAL_CHANCE_FIXED ($PARTIAL_CHANCE_FIXED) " +
                "must leave room for FAIL_CHANCE_MIN ($FAIL_CHANCE_MIN): max non-fail = $maxPossibleNonFail, " +
                "but 100 - FAIL_CHANCE_MIN = ${100 - FAIL_CHANCE_MIN}"
        }
    }
}
