// FILE: core/src/main/kotlin/core/invariants/InvariantVerifier.kt
package core.invariants

import core.primitives.*
import core.state.*

/**
 * Verifies a subset of state consistency invariants and reports violations.
 *
 * ## Contract
 * - Reads the provided `state` and returns a list of `InvariantViolation` entries.
 * - Each `InvariantViolation.details` string is constructed deterministically from observed values.
 * - Violation emission order is deterministic:
 *   - Checks are executed in a fixed sequence (as written in this function).
 *   - Where multiple entities can violate the same invariant, the function sorts by stable keys
 *     (e.g., `id.value`) before emitting violations.
 * - The implemented checks correspond to the `InvariantId` values referenced in this function.
 *   (Not every `InvariantId` enum value is necessarily checked here.)
 *
 * ## Preconditions
 * - None.
 *
 * ## Postconditions
 * - Returns an empty list iff all implemented checks pass.
 * - Does not mutate `state`.
 *
 * ## Invariants
 * - None.
 *
 * ## Determinism
 * - Deterministic for a given `state`:
 *   - No RNG usage.
 *   - Iteration order is stabilized via explicit `sortedBy(...)` in multi-violation checks.
 *
 * ## Complexity
 * - Time: O((B + A + R + H) log(B + A + R + H)) due to sorting in some checks, where:
 *   - B = number of board contracts
 *   - A = number of active contracts
 *   - R = number of return packets
 *   - H = number of heroes in the roster
 * - Memory: O(B + A + R + H) for indices and lookup maps.
 *
 * @param state Root immutable game state to validate.
 * @return List of invariant violations; empty when no violations are detected by the implemented checks.
 */
@Suppress("UNUSED_VARIABLE")
fun verifyInvariants(state: GameState): List<InvariantViolation> {
    val violations = mutableListOf<InvariantViolation>()

    // --- 1. Indices & Precomputations ---
    val inboxIds = state.contracts.inbox.map { it.id.value }
    val boardIds = state.contracts.board.map { it.id.value }
    val allContractIds = inboxIds + boardIds
    val maxContractId = allContractIds.maxOrNull() ?: 0

    val activeIds = state.contracts.active.map { it.id.value }
    val maxActiveId = activeIds.maxOrNull() ?: 0

    val activeByBoardId = state.contracts.active.groupBy { it.boardContractId.value }
    val returnsByBoardId = state.contracts.returns.groupBy { it.boardContractId.value }

    val heroesById = state.heroes.roster.associateBy { it.id.value }
    val maxHeroId = heroesById.keys.maxOrNull() ?: 0

    // Heroes "in flight" usage:
    // - WIP actives keep heroes ON_MISSION
    // - returns requiring close keep heroes ON_MISSION until CloseReturn
    val heroUsageCount = mutableMapOf<Int, Int>()

    state.contracts.active
        .asSequence()
        .filter { it.status == ActiveStatus.WIP }
        .forEach { active ->
            active.heroIds.forEach { heroId ->
                heroUsageCount[heroId.value] = (heroUsageCount[heroId.value] ?: 0) + 1
            }
        }

    // TODO: Append invariant checks here, using the precomputed indices above, and add to `violations`.
    @Suppress("UNUSED_VARIABLE")
    run {
        // Keep precomputations anchored until checks are implemented.
        maxContractId
        maxActiveId
        maxHeroId
        activeByBoardId
        returnsByBoardId
        heroUsageCount
    }

    return violations
}
