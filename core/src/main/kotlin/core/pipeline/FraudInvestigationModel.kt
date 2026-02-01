// FILE: core/src/main/kotlin/core/pipeline/FraudInvestigationModel.kt
package core.pipeline

import core.BalanceSettings
import core.primitives.HeroStatus
import core.primitives.ProofPolicy
import core.rng.Rng
import core.state.Hero
import core.state.ReturnPacket

/**
 * Fraud investigation decision model.
 *
 * ## Semantic Ownership
 * Answers: **What happens when fraud is suspected?**
 *
 * Implements the linear investigation pipeline:
 * `suspectedTheft -> investigation_roll -> (caught | got_away) -> (warn/ban | rumorScheduled)`
 *
 * ## Contract
 * - Triggered only when `suspectedTheft == true` on a return.
 * - Investigation is policy-dependent (SOFT vs STRICT catch/rumor probabilities).
 * - FAST policy does not trigger investigation (legacy behavior).
 *
 * ## Invariants
 * - Investigation order is deterministic (by activeContractId ascending).
 * - RNG draws are exactly 1 for catch roll, optionally 1 for rumor roll if not caught.
 * - No state mutation; returns decision DTOs.
 *
 * ## Determinism
 * - RNG draw order: catch roll first, rumor roll second (only if not caught).
 * - All inputs are explicit; no hidden state.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state directly.
 */
object FraudInvestigationModel {

    /**
     * Investigates a fraud candidate (return with `suspectedTheft == true`).
     *
     * ## Contract
     * - Returns [InvestigationDecision] with all outcomes.
     * - If policy is FAST, returns a no-op decision (no investigation).
     *
     * ## Preconditions
     * - [returnPacket].suspectedTheft should be true for meaningful investigation.
     *
     * ## Postconditions
     * - RNG is advanced by 1 (catch roll) + optionally 1 (rumor roll if not caught).
     * - Decision contains hero penalty (warn/ban) and rumor scheduling info.
     *
     * ## Determinism
     * - Deterministic for fixed RNG state and inputs.
     *
     * @param hero The hero under investigation (nullable for fallback).
     * @param returnPacket The return that triggered investigation.
     * @param policy Current proof policy.
     * @param currentDay Current day index for computing penalty expiration.
     * @param rng Deterministic RNG.
     * @return [InvestigationDecision] with investigation outcome.
     */
    fun investigate(
        hero: Hero?,
        returnPacket: ReturnPacket,
        policy: ProofPolicy,
        currentDay: Int,
        rng: Rng
    ): InvestigationDecision {
        // FAST policy: no investigation (legacy behavior)
        if (policy == ProofPolicy.FAST) {
            return InvestigationDecision(
                heroId = hero?.id?.value ?: -1,
                activeContractId = returnPacket.activeContractId.value,
                policy = policy,
                investigated = false,
                caught = false,
                rumorScheduled = false,
                penaltyType = PenaltyType.NONE,
                penaltyUntilDay = null,
                repDeltaPlanned = 0
            )
        }

        // No hero means no investigation target
        if (hero == null) {
            return InvestigationDecision(
                heroId = -1,
                activeContractId = returnPacket.activeContractId.value,
                policy = policy,
                investigated = false,
                caught = false,
                rumorScheduled = false,
                penaltyType = PenaltyType.NONE,
                penaltyUntilDay = null,
                repDeltaPlanned = 0
            )
        }

        // Not a fraud candidate
        if (!returnPacket.suspectedTheft) {
            return InvestigationDecision(
                heroId = hero.id.value,
                activeContractId = returnPacket.activeContractId.value,
                policy = policy,
                investigated = false,
                caught = false,
                rumorScheduled = false,
                penaltyType = PenaltyType.NONE,
                penaltyUntilDay = null,
                repDeltaPlanned = 0
            )
        }

        // Step 1: Investigation roll
        val catchChance = getCatchChance(policy)
        val catchRoll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
        val caught = catchRoll < catchChance

        if (caught) {
            // Step 2a: Caught - determine penalty
            val hasActiveWarn = isWarnActive(hero, currentDay)
            return if (hasActiveWarn) {
                // Escalate to BAN
                val banUntilDay = currentDay + BalanceSettings.BAN_DURATION_DAYS
                InvestigationDecision(
                    heroId = hero.id.value,
                    activeContractId = returnPacket.activeContractId.value,
                    policy = policy,
                    investigated = true,
                    caught = true,
                    rumorScheduled = false,
                    penaltyType = PenaltyType.BAN,
                    penaltyUntilDay = banUntilDay,
                    repDeltaPlanned = 0
                )
            } else {
                // First offense - WARN
                val warnUntilDay = currentDay + BalanceSettings.WARN_DURATION_DAYS
                InvestigationDecision(
                    heroId = hero.id.value,
                    activeContractId = returnPacket.activeContractId.value,
                    policy = policy,
                    investigated = true,
                    caught = true,
                    rumorScheduled = false,
                    penaltyType = PenaltyType.WARN,
                    penaltyUntilDay = warnUntilDay,
                    repDeltaPlanned = 0
                )
            }
        } else {
            // Step 2b: Got away - roll for rumor
            val rumorChance = getRumorChance(policy)
            val rumorRoll = rng.nextInt(BalanceSettings.PERCENT_ROLL_MAX)
            val rumorScheduled = rumorRoll < rumorChance

            val repDelta = if (rumorScheduled) -BalanceSettings.REP_PENALTY_ON_RUMOR else 0

            return InvestigationDecision(
                heroId = hero.id.value,
                activeContractId = returnPacket.activeContractId.value,
                policy = policy,
                investigated = true,
                caught = false,
                rumorScheduled = rumorScheduled,
                penaltyType = PenaltyType.NONE,
                penaltyUntilDay = null,
                repDeltaPlanned = repDelta
            )
        }
    }

    /**
     * Determines the effective disciplinary status of a hero.
     *
     * ## Contract
     * - Returns derived status based on warnUntilDay and banUntilDay.
     * - BAN takes precedence over WARN.
     *
     * @param hero The hero to check.
     * @param currentDay Current day index.
     * @return Effective [HeroStatus] (BANNED, WARNED, or original status).
     */
    fun getEffectiveStatus(hero: Hero, currentDay: Int): HeroStatus {
        if (isBanActive(hero, currentDay)) return HeroStatus.BANNED
        if (isWarnActive(hero, currentDay)) return HeroStatus.WARNED
        return hero.status
    }

    /**
     * Checks if the hero is currently banned.
     *
     * @param hero The hero to check.
     * @param currentDay Current day index.
     * @return True if ban is active.
     */
    fun isBanActive(hero: Hero, currentDay: Int): Boolean {
        val banUntil = hero.banUntilDay ?: return false
        return currentDay < banUntil
    }

    /**
     * Checks if the hero is currently warned (and not banned).
     *
     * @param hero The hero to check.
     * @param currentDay Current day index.
     * @return True if warn is active (and ban is not).
     */
    fun isWarnActive(hero: Hero, currentDay: Int): Boolean {
        if (isBanActive(hero, currentDay)) return false
        val warnUntil = hero.warnUntilDay ?: return false
        return currentDay < warnUntil
    }

    /**
     * Gets the catch probability for the given policy.
     */
    private fun getCatchChance(policy: ProofPolicy): Int {
        return when (policy) {
            ProofPolicy.STRICT -> BalanceSettings.CATCH_CHANCE_STRICT_PERCENT
            ProofPolicy.SOFT -> BalanceSettings.CATCH_CHANCE_SOFT_PERCENT
            ProofPolicy.FAST -> 0 // No investigation
        }
    }

    /**
     * Gets the rumor probability for the given policy.
     */
    private fun getRumorChance(policy: ProofPolicy): Int {
        return when (policy) {
            ProofPolicy.STRICT -> BalanceSettings.RUMOR_CHANCE_ON_ESCAPE_STRICT_PERCENT
            ProofPolicy.SOFT -> BalanceSettings.RUMOR_CHANCE_ON_ESCAPE_SOFT_PERCENT
            ProofPolicy.FAST -> 0 // No rumors
        }
    }
}

/**
 * Type of penalty applied to a hero.
 */
enum class PenaltyType {
    /** No penalty applied. */
    NONE,
    /** Warning penalty (first offense). */
    WARN,
    /** Ban penalty (repeat offense). */
    BAN
}

/**
 * Decision output from fraud investigation.
 *
 * ## Contract
 * This DTO captures all outcomes of a fraud investigation.
 * It is RNG-free after creation and can be tested in isolation.
 *
 * @property heroId ID of the investigated hero.
 * @property activeContractId ID of the active contract that triggered investigation.
 * @property policy Proof policy in effect.
 * @property investigated Whether investigation was performed.
 * @property caught Whether the hero was caught.
 * @property rumorScheduled Whether a rumor was scheduled (only when not caught).
 * @property penaltyType Type of penalty applied (WARN, BAN, or NONE).
 * @property penaltyUntilDay Day when penalty expires (null if no penalty).
 * @property repDeltaPlanned Planned reputation delta from rumors (negative or 0).
 */
data class InvestigationDecision(
    val heroId: Int,
    val activeContractId: Int,
    val policy: ProofPolicy,
    val investigated: Boolean,
    val caught: Boolean,
    val rumorScheduled: Boolean,
    val penaltyType: PenaltyType,
    val penaltyUntilDay: Int?,
    val repDeltaPlanned: Int
)
