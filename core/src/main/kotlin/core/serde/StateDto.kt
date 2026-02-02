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
 *
 * @property meta Meta state (counters, revision, tax).
 * @property guild Guild state (rank/progression/policy).
 * @property region Region simulation state.
 * @property economy Economy state (money/trophies/reserved).
 * @property contracts Contract lifecycle state.
 * @property heroes Hero roster state.
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
 *
 * @property saveVersion Save format version.
 * @property seed State seed (UInt) used for determinism.
 * @property dayIndex Current day index.
 * @property revision Monotonic state revision.
 * @property ids Monotonic id counters.
 * @property taxDueDay Next due day for taxes.
 * @property taxAmountDue Current cycle amount due.
 * @property taxPenalty Accumulated penalty.
 * @property taxMissedCount Missed payment strike counter.
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
 *
 * @property nextContractId Next contract id.
 * @property nextHeroId Next hero id.
 * @property nextActiveContractId Next active contract id.
 */
@Serializable
data class IdCountersDto(
    val nextContractId: Int,
    val nextHeroId: Int,
    val nextActiveContractId: Int
)

/**
 * DTO for guild progression and policy state.
 *
 * @property guildRank Guild rank (stored as ordinal/int).
 * @property reputation Guild reputation.
 * @property completedContractsTotal Total completed contracts.
 * @property contractsForNextRank Remaining contracts for next rank.
 * @property proofPolicy Proof policy name (e.g. FAST/STRICT).
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
 *
 * @property stability Regional stability (0..100).
 */
@Serializable
data class RegionStateDto(
    val stability: Int
)

/**
 * DTO for economy state.
 *
 * @property moneyCopper Money stored in copper.
 * @property trophiesStock Total trophies held.
 * @property reservedCopper Reserved copper (escrow).
 */
@Serializable
data class EconomyStateDto(
    val moneyCopper: Int,
    val trophiesStock: Int,
    val reservedCopper: Int
)

/**
 * DTO for contract lifecycle collections.
 *
 * @property inbox Inbox drafts.
 * @property board Posted board contracts.
 * @property archive Archived/completed board contracts.
 * @property active Active in-progress contracts.
 * @property returns Return packets awaiting close/processing.
 */
@Serializable
data class ContractStateDto(
    val inbox: List<ContractDraftDto>,
    val board: List<BoardContractDto>,
    val archive: List<BoardContractDto>,
    val active: List<ActiveContractDto>,
    val returns: List<ReturnPacketDto>
)

/**
 * DTO for contract draft in inbox.
 *
 * @property id Contract id.
 * @property createdDay Created day index.
 * @property nextAutoResolveDay Auto resolve day index.
 * @property title Title.
 * @property rankSuggested Suggested rank name.
 * @property feeOffered Offered fee in copper.
 * @property salvage Salvage policy name.
 * @property baseDifficulty Difficulty scalar.
 * @property proofHint Proof hint string.
 * @property clientDeposit Client deposit in copper.
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
 *
 * @property id Contract id.
 * @property postedDay Posted day index.
 * @property title Title.
 * @property rank Rank name.
 * @property fee Fee in copper.
 * @property salvage Salvage policy name.
 * @property baseDifficulty Difficulty scalar.
 * @property status Board status name.
 * @property clientDeposit Client deposit in copper.
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
 *
 * @property id Active contract id.
 * @property boardContractId Board contract id.
 * @property takenDay Taken day index.
 * @property daysRemaining Days remaining.
 * @property heroIds Participating hero ids.
 * @property status Active status name.
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
 *
 * @property activeContractId Active contract id.
 * @property boardContractId Board contract id.
 * @property heroIds Participating hero ids.
 * @property resolvedDay Resolved day index.
 * @property outcome Outcome name.
 * @property trophiesCount Trophy count.
 * @property trophiesQuality Quality name.
 * @property reasonTags Reason tags.
 * @property requiresPlayerClose Whether player close is required.
 * @property suspectedTheft Whether theft is suspected.
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
 *
 * @property roster List of heroes.
 */
@Serializable
data class HeroStateDto(
    val roster: List<HeroDto>
)

/**
 * DTO for individual hero entity.
 *
 * @property id Hero id.
 * @property name Display name.
 * @property rank Rank name.
 * @property klass Class name.
 * @property traits Personality traits.
 * @property status Hero status name.
 * @property historyCompleted Completed missions count.
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
 *
 * @property greed Greed trait (0..100).
 * @property honesty Honesty trait (0..100).
 * @property courage Courage trait (0..100).
 */
@Serializable
data class TraitsDto(
    val greed: Int,
    val honesty: Int,
    val courage: Int
)
