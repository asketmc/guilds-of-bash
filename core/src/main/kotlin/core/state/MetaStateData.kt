package core.state

data class MetaState(
    val saveVersion: Int,
    val seed: UInt,
    val dayIndex: Int,
    val revision: Long,
    val ids: IdCounters,

    // Tax system (Phase 2)
    val taxDueDay: Int,
    val taxAmountDue: Int,
    val taxPenalty: Int,
    val taxMissedCount: Int
)

data class IdCounters(
    val nextContractId: Int,
    val nextHeroId: Int,
    val nextActiveContractId: Int
)
