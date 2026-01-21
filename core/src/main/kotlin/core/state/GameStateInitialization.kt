package core.state

import core.primitives.*

fun initialState(seed: UInt): GameState {
    // Provide a small initial inbox so tests can assume at least one draft exists
    val initialInbox = listOf(
        ContractDraft(
            id = ContractId(1),
            createdDay = 0,
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
