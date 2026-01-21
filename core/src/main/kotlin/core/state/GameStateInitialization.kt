package core.state

import core.primitives.*

fun initialState(seed: UInt): GameState {
    return GameState(
        meta = MetaState(
            saveVersion = 1,
            seed = seed,
            dayIndex = 0,
            revision = 0L,
            ids = IdCounters(
                nextContractId = 1,
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
            inbox = emptyList(),
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
