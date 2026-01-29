// FILE: core/src/main/kotlin/core/handlers/GovernanceHandlers.kt
package core.handlers

import core.*
import core.pipeline.GovernancePolicy
import core.primitives.*
import core.rng.Rng
import core.state.*

/**
 * Governance command handlers.
 *
 * ## Semantic Ownership
 * Handles all governance-related commands: proof policy changes.
 *
 * ## Visibility
 * Internal to core module - only Reducer.kt should call these.
 */

/**
 * Changes the guild's proof policy.
 *
 * Why:
 * - Proof policy is a governance switch.
 * - It must be explicit to keep replays comparable.
 *
 * How:
 * - Emits a change event and updates guild policy in one step.
 */
@Suppress("UNUSED_PARAMETER")
internal fun handleSetProofPolicy(
    state: GameState,
    cmd: SetProofPolicy,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val change = GovernancePolicy.computePolicyChange(
        oldPolicy = state.guild.proofPolicy,
        newPolicy = cmd.policy
    )

    if (change.changed) {
        ctx.emit(
            ProofPolicyChanged(
                day = state.meta.dayIndex,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                oldPolicy = change.oldPolicy.ordinal,
                newPolicy = change.newPolicy.ordinal
            )
        )
    }

    return state.copy(guild = state.guild.copy(proofPolicy = change.newPolicy))
}
