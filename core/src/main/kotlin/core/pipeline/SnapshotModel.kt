// FILE: core/src/main/kotlin/core/pipeline/SnapshotModel.kt
package core.pipeline

import core.DaySnapshot
import core.primitives.ActiveStatus
import core.state.GameState

/**
 * Snapshot construction model.
 *
 * ## Semantic Ownership
 * Answers: **What are the key metrics at day end?**
 *
 * ## Stability Gradient
 * STABLE: Pure extraction from state.
 *
 * ## Determinism
 * - No RNG usage. All inputs are explicit.
 *
 * ## Boundary Rules
 * - Must NOT emit events.
 * - Must NOT mutate state.
 */
object SnapshotModel {

    /**
     * Creates a day-end snapshot from the current game state.
     *
     * @param state Current game state.
     * @param day Day index for the snapshot.
     * @return [DaySnapshot] with key metrics.
     */
    fun createDaySnapshot(state: GameState, day: Int): DaySnapshot {
        return DaySnapshot(
            day = day,
            revision = state.meta.revision,
            money = state.economy.moneyCopper,
            trophies = state.economy.trophiesStock,
            regionStability = state.region.stability,
            guildReputation = state.guild.reputation,
            inboxCount = state.contracts.inbox.size,
            boardCount = state.contracts.board.size,
            activeCount = state.contracts.active.count { it.status == ActiveStatus.WIP },
            returnsNeedingCloseCount = state.contracts.returns.count { it.requiresPlayerClose }
        )
    }
}
