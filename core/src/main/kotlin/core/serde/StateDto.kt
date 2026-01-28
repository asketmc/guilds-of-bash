package core.serde

import kotlinx.serialization.Serializable

/**
 * DTOs for canonical JSON serialization.
 * Value classes are serialized as raw Int.
 * arrivalsToday is NOT serialized (dropped on save, empty on load).
 */

@Serializable
data class GameStateDto(
    val meta: MetaStateDto,
    val guild: GuildStateDto,
    val region: RegionStateDto,
    val economy: EconomyStateDto,
    val contracts: ContractStateDto,
    val heroes: HeroStateDto
)

@Serializable
data class MetaStateDto(
    val saveVersion: Int,
    val seed: UInt,
    val dayIndex: Int,
    val revision: Long,
    val ids: IdCountersDto,

    // Tax fields
    val taxDueDay: Int,
    val taxAmountDue: Int,
    val taxPenalty: Int,
    val taxMissedCount: Int
)

@Serializable
data class IdCountersDto(
    val nextContractId: Int,
    val nextHeroId: Int,
    val nextActiveContractId: Int
)

@Serializable
data class GuildStateDto(
    val guildRank: Int,
    val reputation: Int,

    // Rank progression
    val completedContractsTotal: Int,
    val contractsForNextRank: Int,

    // Proof validation policy (Phase 3)
    val proofPolicy: String = "FAST"
)

@Serializable
data class RegionStateDto(
    val stability: Int
)

@Serializable
data class EconomyStateDto(
    val moneyCopper: Int,
    val trophiesStock: Int,
    val reservedCopper: Int
)

@Serializable
data class ContractStateDto(
    val inbox: List<ContractDraftDto>,
    val board: List<BoardContractDto>,
    val active: List<ActiveContractDto>,
    val returns: List<ReturnPacketDto>
)

@Serializable
data class ContractDraftDto(
    val id: Int, // ContractId as raw Int
    val createdDay: Int,
    val nextAutoResolveDay: Int,
    val title: String,
    val rankSuggested: String, // Rank enum as string
    val feeOffered: Int,
    val salvage: String, // SalvagePolicy enum as string
    val baseDifficulty: Int,
    val proofHint: String,
    val clientDeposit: Int = 0
)

@Serializable
data class BoardContractDto(
    val id: Int, // ContractId as raw Int
    val postedDay: Int,
    val title: String,
    val rank: String, // Rank enum as string
    val fee: Int,
    val salvage: String, // SalvagePolicy enum as string
    val baseDifficulty: Int,
    val status: String, // BoardStatus enum as string
    val clientDeposit: Int = 0
)

@Serializable
data class ActiveContractDto(
    val id: Int, // ActiveContractId as raw Int
    val boardContractId: Int, // ContractId as raw Int
    val takenDay: Int,
    val daysRemaining: Int,
    val heroIds: List<Int>, // List<HeroId> as List<Int>
    val status: String // ActiveStatus enum as string
)

@Serializable
data class ReturnPacketDto(
    val activeContractId: Int, // ActiveContractId as raw Int
    val boardContractId: Int, // ContractId as raw Int
    val heroIds: List<Int>, // List<HeroId> as List<Int>
    val resolvedDay: Int,
    val outcome: String, // Outcome enum as string
    val trophiesCount: Int,
    val trophiesQuality: String, // Quality enum as string
    val reasonTags: List<String>,
    val requiresPlayerClose: Boolean,
    val suspectedTheft: Boolean
)

@Serializable
data class HeroStateDto(
    val roster: List<HeroDto>
    // arrivalsToday is NOT included (K11 decision)
)

@Serializable
data class HeroDto(
    val id: Int, // HeroId as raw Int
    val name: String,
    val rank: String, // Rank enum as string
    val klass: String, // HeroClass enum as string
    val traits: TraitsDto,
    val status: String, // HeroStatus enum as string
    val historyCompleted: Int
)

@Serializable
data class TraitsDto(
    val greed: Int,
    val honesty: Int,
    val courage: Int
)
