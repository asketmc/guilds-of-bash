package core.state

import core.primitives.*

data class ContractState(
    val inbox: List<ContractDraft>,
    val board: List<BoardContract>,
    val active: List<ActiveContract>,
    val returns: List<ReturnPacket>
)

data class ContractDraft(
    val id: ContractId,
    val createdDay: Int,
    val title: String,
    val rankSuggested: Rank,
    val feeOffered: Int,
    val baseDifficulty: Int,
    val proofHint: String
)

data class BoardContract(
    val id: ContractId,
    val postedDay: Int,
    val title: String,
    val rank: Rank,
    val fee: Int,
    val salvage: SalvagePolicy,
    val status: BoardStatus
)

data class ActiveContract(
    val id: ActiveContractId,
    val boardContractId: ContractId,
    val takenDay: Int,
    val daysRemaining: Int,
    val heroIds: List<HeroId>,
    val status: ActiveStatus
)

data class ReturnPacket(
    val activeContractId: ActiveContractId,
    val boardContractId: ContractId,
    val heroIds: List<HeroId>,
    val resolvedDay: Int,
    val outcome: Outcome,
    val trophiesCount: Int,
    val trophiesQuality: Quality,
    val reasonTags: List<String>,
    val requiresPlayerClose: Boolean
)
