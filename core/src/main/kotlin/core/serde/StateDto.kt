package core.serde

import kotlinx.serialization.Serializable

/**
 * Data Transfer Objects for canonical JSON serialization of game state.
 *
 * ## Contract
 * - Value classes are serialized as raw Int.
 * - Enums are serialized as String (name).
 * - `arrivalsToday` is NOT serialized (dropped on save, empty on load).
 *
 * ## Stability
 * - Stable for save format version 1.
 */

/**
 * Root DTO for complete game state serialization.
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

/**
 * DTO for meta state including counters and tax tracking.
 */
@Serializable
data class MetaStateDto(
    val saveVersion: Int,
    val seed: UInt,
    val dayIndex: Int,
    val revision: Long,
    val ids: IdCountersDto,
    val taxDueDay: Int,
    val taxAmountDue: Int,
    val taxPenalty: Int,
    val taxMissedCount: Int
)

/**
 * DTO for monotonic ID counters.
 */
@Serializable
data class IdCountersDto(
    val nextContractId: Int,
    val nextHeroId: Int,
    val nextActiveContractId: Int
)

/**
 * DTO for guild progression and policy state.
 */
@Serializable
data class GuildStateDto(
    val guildRank: Int,
    val reputation: Int,
    val completedContractsTotal: Int,
    val contractsForNextRank: Int,
    val proofPolicy: String = "FAST"
)

/**
 * DTO for regional simulation state.
 */
@Serializable
data class RegionStateDto(
    val stability: Int
)

/**
 * DTO for economy state.
 */
@Serializable
data class EconomyStateDto(
    val moneyCopper: Int,
    val trophiesStock: Int,
    val reservedCopper: Int
)

/**
 * DTO for contract lifecycle collections.
 */
@Serializable
data class ContractStateDto(
    val inbox: List<ContractDraftDto>,
    val board: List<BoardContractDto>,
    val active: List<ActiveContractDto>,
    val returns: List<ReturnPacketDto>
)

/**
 * DTO for contract draft in inbox.
 */
@Serializable
data class ContractDraftDto(
    val id: Int,
    val createdDay: Int,
    val nextAutoResolveDay: Int,
    val title: String,
    val rankSuggested: String,
    val feeOffered: Int,
    val salvage: String,
    val baseDifficulty: Int,
    val proofHint: String,
    val clientDeposit: Int = 0
)

/**
 * DTO for posted board contract.
 */
@Serializable
data class BoardContractDto(
    val id: Int,
    val postedDay: Int,
    val title: String,
    val rank: String,
    val fee: Int,
    val salvage: String,
    val baseDifficulty: Int,
    val status: String,
    val clientDeposit: Int = 0
)

/**
 * DTO for active in-progress contract.
 */
@Serializable
data class ActiveContractDto(
    val id: Int,
    val boardContractId: Int,
    val takenDay: Int,
    val daysRemaining: Int,
    val heroIds: List<Int>,
    val status: String
)

/**
 * DTO for resolved contract return packet.
 */
@Serializable
data class ReturnPacketDto(
    val activeContractId: Int,
    val boardContractId: Int,
    val heroIds: List<Int>,
    val resolvedDay: Int,
    val outcome: String,
    val trophiesCount: Int,
    val trophiesQuality: String,
    val reasonTags: List<String>,
    val requiresPlayerClose: Boolean,
    val suspectedTheft: Boolean
)

/**
 * DTO for hero roster (arrivalsToday excluded).
 */
@Serializable
data class HeroStateDto(
    val roster: List<HeroDto>
)

/**
 * DTO for individual hero entity.
 */
@Serializable
data class HeroDto(
    val id: Int,
    val name: String,
    val rank: String,
    val klass: String,
    val traits: TraitsDto,
    val status: String,
    val historyCompleted: Int
)

/**
 * DTO for hero personality traits.
 */
@Serializable
data class TraitsDto(
    val greed: Int,
    val honesty: Int,
    val courage: Int
)
