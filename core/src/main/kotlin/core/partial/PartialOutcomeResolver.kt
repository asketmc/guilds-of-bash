package core.partial

import core.primitives.Outcome
import core.primitives.Quality

/**
 * Adapter-free, deterministic normalization for PARTIAL resolution.
 *
 * Contract:
 * - Only applies to Outcome.PARTIAL.
 * - Uses integer math (floor) for stability/diff-friendliness.
 * - No side effects.
 */
object PartialOutcomeResolver {

    fun resolve(input: PartialResolutionInput): PartialResolutionOutput {
        if (input.outcome != Outcome.PARTIAL) {
            return PartialResolutionOutput(
                outcome = input.outcome,
                moneyValueCopper = input.normalMoneyValueCopper,
                flags = emptySet()
            )
        }

        val money = input.normalMoneyValueCopper / 2

        return PartialResolutionOutput(
            outcome = input.outcome,
            moneyValueCopper = money,
            flags = setOf(PartialResolutionFlag.PARTIAL_APPLIED)
        )
    }
}

/**
 * Input to PARTIAL resolution normalization.
 */
data class PartialResolutionInput(
    val outcome: Outcome,
    val normalMoneyValueCopper: Int,
    val trophiesCount: Int,
    val trophiesQuality: TrophiesQuality,
    val suspectedTheft: Boolean
)

/**
 * Output of PARTIAL resolution normalization.
 */
data class PartialResolutionOutput(
    val outcome: Outcome,
    val moneyValueCopper: Int,
    val flags: Set<PartialResolutionFlag>
)

enum class PartialResolutionFlag {
    PARTIAL_APPLIED
}

/**
 * Thin stable wrapper in the partial module over core [Quality] to keep future refactors localized.
 */
enum class TrophiesQuality {
    OK,
    DAMAGED;

    fun toCoreQuality(): Quality = when (this) {
        OK -> Quality.OK
        DAMAGED -> Quality.DAMAGED
    }

    companion object {
        fun fromCoreQuality(q: Quality): TrophiesQuality = when (q) {
            Quality.OK -> OK
            Quality.DAMAGED -> DAMAGED
            Quality.NONE -> DAMAGED
        }
    }
}
