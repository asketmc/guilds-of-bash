// FILE: core/src/main/kotlin/core/state/GameStateInitialization.kt
package core.state

import core.primitives.*

/**
 * Creates the initial deterministic [GameState] for a new run.
 *
 * ## Contract
 * Constructs an initial state at dayIndex=0 with initialized id counters, default guild/region/economy values,
 * and a small non-empty contract inbox to satisfy tests and bootstrap gameplay.
 *
 * ## Preconditions
 * - [seed] may be any unsigned 32-bit value (`UInt`).
 *
 * ## Postconditions
 * - Returned [GameState.meta.seed] equals [seed].
 * - Returned [GameState.meta.dayIndex] == 0.
 * - Returned [GameState.meta.revision] == 0.
 * - Returned [GameState.contracts.inbox] is non-empty (currently 2 drafts with ids 1 and 2).
 * - Returned [GameState.meta.ids.nextContractId] is greater than any draft id present in the inbox.
 * - Returned [GameState.contracts.board], [GameState.contracts.active], [GameState.contracts.returns] are empty.
 * - Returned [GameState.heroes.roster] and [GameState.heroes.arrivalsToday] are empty.
 *
 * ## Invariants
 * - All sub-states satisfy baseline non-negativity expectations (money/trophies/escrow, counters, day indexes).
 * - ID counters in [MetaState.ids] are expected to be non-negative and monotonic for the lifetime of the run.
 *
 * ## Determinism
 * Deterministic: for a given [seed], this function returns the same initial state. Any later randomness should
 * be derived from [seed] via the core RNG strategy.
 *
 * ## Complexity
 * - Time: O(1) (fixed-size initialization).
 * - Memory: O(1) (fixed-size allocations).
 *
 * @param seed Unsigned 32-bit seed used as the root of deterministic RNG streams for the run.
 * @return Initialized [GameState] for day 0.
 */
fun initialState(seed: UInt): GameState {
    // Provide a small initial inbox so tests can assume at least one draft exists
    val initialInbox = listOf(
        ContractDraft(
            id = ContractId(1),
            createdDay = 0,
            nextAutoResolveDay = 7,
            title = "Request #1",
            rankSuggested = Rank.F,
            feeOffered = 0,
            salvage = SalvagePolicy.GUILD,
            baseDifficulty = 1,
            proofHint = "proof"
        ),
        ContractDraft(
            id = ContractId(2),
            createdDay = 0,
            nextAutoResolveDay = 7,
            title = "Request #2",
            rankSuggested = Rank.F,
            feeOffered = 0,
            salvage = SalvagePolicy.GUILD,
            baseDifficulty = 1,
            proofHint = "proof"
        )
    )

    val nextId = (initialInbox.maxOfOrNull { it.id.value } ?: 0) + 1

    return GameState(
        meta = MetaState(
            saveVersion = 1,
            seed = seed,
            dayIndex = 0,
            revision = 0L,
            ids = IdCounters(
                nextContractId = nextId,
                nextHeroId = 1,
                nextActiveContractId = 1
            ),

            // Tax initial state
            taxDueDay = 7,
            taxAmountDue = 10,
            taxPenalty = 0,
            taxMissedCount = 0
        ),
        guild = GuildState(
            guildRank = 1,
            reputation = 50,
            completedContractsTotal = 0,
            contractsForNextRank = 10,
            proofPolicy = core.primitives.ProofPolicy.FAST
        ),
        region = RegionState(
            stability = 50
        ),
        economy = EconomyState(
            moneyCopper = 100,
            reservedCopper = 0,
            trophiesStock = 0
        ),
        contracts = ContractState(
            inbox = initialInbox,
            board = emptyList(),
            active = emptyList(),
            returns = emptyList()
        ),
        heroes = HeroState(
            roster = emptyList(),
            arrivalsToday = emptyList()
        )
    )
}
