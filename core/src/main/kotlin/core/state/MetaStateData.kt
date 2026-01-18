package core.state

data class MetaState(
    val saveVersion: Int,
    val seed: UInt,
    val dayIndex: Int,
    val revision: Long,
    val ids: IdCounters
)

data class IdCounters(
    val nextContractId: Int,
    val nextHeroId: Int,
    val nextActiveContractId: Int
)
