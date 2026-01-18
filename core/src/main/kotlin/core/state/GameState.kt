package core.state

data class GameState(
    val meta: MetaState,
    val guild: GuildState,
    val region: RegionState,
    val economy: EconomyState,
    val contracts: ContractState,
    val heroes: HeroState
)
