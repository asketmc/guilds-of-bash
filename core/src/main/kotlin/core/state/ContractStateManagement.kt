// FILE: core/src/main/kotlin/core/state/ContractStateManagement.kt
package core.state

import core.primitives.*

/**
 * Aggregate contract-related state owned by the core reducer.
 *
 * ## Contract
 * Holds all contract records grouped by lifecycle stage:
 * - inbox: drafts available for posting
 * - board: posted contracts visible for pickup
 * - active: taken contracts currently in progress
 * - returns: resolved contracts awaiting explicit close (player interaction)
 *
 * ## Invariants
 * - IDs are expected to be unique within each collection (by their respective id type).
 * - A single logical contract should not appear in multiple lifecycle collections simultaneously
 *   (e.g., a draft should not remain in [inbox] after being represented as a [BoardContract] on [board]).
 * - References are expected to be valid:
 *   - [ActiveContract.boardContractId] should point to an existing [BoardContract.id] on [board].
 *   - [ReturnPacket.boardContractId] should point to an existing [BoardContract.id] on [board].
 * - Ordering is not semantically relevant unless explicitly relied upon by callers; when ordering matters,
 *   prefer sorting by id/value at use sites.
 *
 * ## Determinism
 * Pure data container. Determinism is defined by the producer (e.g., reducer); serialization/hash stability
 * depends on using canonical ordering/serialization.
 *
 * @property inbox Draft contracts ([ContractDraft]) waiting to be posted to the board.
 * @property board Posted contracts ([BoardContract]) available for pickup.
 * @property active In-progress contracts ([ActiveContract]) indexed by [ActiveContractId].
 * @property returns Return records ([ReturnPacket]) pending explicit close when [ReturnPacket.requiresPlayerClose] is true.
 */
data class ContractState(
    val inbox: List<ContractDraft>,
    val board: List<BoardContract>,
    val active: List<ActiveContract>,
    val returns: List<ReturnPacket>
)

/**
 * Draft contract created by generation or explicit commands, not yet posted to the board.
 *
 * ## Contract
 * Represents a candidate contract with suggested terms. Posting converts a draft into a [BoardContract]
 * (and typically removes it from [ContractState.inbox]).
 *
 * ## Invariants
 * - [id] is stable and unique among drafts in the same state.
 * - [createdDay] is a day-index (expected >= 0).
 * - [feeOffered] is in copper units (expected >= 0).
 * - [baseDifficulty] is a non-negative difficulty scalar.
 *   Current implementations may use:
 *   - 1..5 (threat-scaling generation)
 *   - 0..100 (validation path for explicit creation)
 * - [title] is expected to be non-blank for player-visible drafts (validated for some creation paths).
 *
 * ## Determinism
 * Pure data container. Draft generation is deterministic iff driven only by deterministic inputs (e.g., seed + dayIndex).
 *
 * @property id Stable identifier of this draft ([ContractId]).
 * @property createdDay Day-index when the draft was created (>= 0).
 * @property title Human-readable title.
 * @property rankSuggested Suggested guild/hero rank ([Rank]) for the contract.
 * @property feeOffered Offered fee in copper currency units (>= 0).
 * @property salvage Salvage ownership policy ([SalvagePolicy]) proposed for posting.
 * @property baseDifficulty Difficulty scalar (non-negative; current PoC uses 1..5+ or 0..100 depending on source).
 * @property proofHint Human-readable hint for proof/validation flows.
 */
data class ContractDraft(
    val id: ContractId,
    val createdDay: Int,
    val title: String,
    val rankSuggested: Rank,
    val feeOffered: Int,
    val salvage: SalvagePolicy,
    val baseDifficulty: Int,
    val proofHint: String
)

/**
 * Posted contract visible for hero pickup.
 *
 * ## Contract
 * Board entries are derived from [ContractDraft] at posting time, potentially with adjusted terms
 * (fee/salvage). Board entries may transition through [BoardStatus] (e.g., OPEN -> LOCKED -> COMPLETED).
 *
 * ## Invariants
 * - [id] is stable and unique among board entries.
 * - [postedDay] is a day-index (expected >= 0).
 * - [fee] is in copper units (expected >= 0).
 * - [baseDifficulty] is a non-negative difficulty scalar; current PoC commonly treats it as 1..5+.
 *
 * ## Determinism
 * Pure data container. Board creation is deterministic iff posting logic is deterministic for a given input state/command.
 *
 * @property id Contract identifier ([ContractId]); typically carried over from the originating draft.
 * @property postedDay Day-index when the contract was posted (>= 0).
 * @property title Human-readable title.
 * @property rank Target rank requirement ([Rank]).
 * @property fee Escrowed fee in copper currency units (>= 0).
 * @property salvage Salvage ownership policy ([SalvagePolicy]) applied to this posted contract.
 * @property baseDifficulty Difficulty scalar (non-negative; current PoC uses 1..5+ scale).
 * @property status Current board lifecycle status ([BoardStatus]).
 */
data class BoardContract(
    val id: ContractId,
    val postedDay: Int,
    val title: String,
    val rank: Rank,
    val fee: Int,
    val salvage: SalvagePolicy,
    val baseDifficulty: Int,  // Difficulty from original draft (1-5+ scale)
    val status: BoardStatus
)

/**
 * In-progress contract taken by one or more heroes.
 *
 * ## Contract
 * Represents a running mission derived from a [BoardContract]. Progress is modeled through [daysRemaining]
 * and [status] (e.g., WIP -> RETURN_READY -> CLOSED).
 *
 * ## Invariants
 * - [id] is stable and unique among active contracts.
 * - [boardContractId] references a posted [BoardContract.id] on the board.
 * - [takenDay] is a day-index (expected >= 0).
 * - [daysRemaining] is in days (expected >= 0).
 * - [heroIds] are expected to reference existing heroes in [HeroState.roster].
 *
 * ## Determinism
 * Pure data container. Active creation/progression is deterministic iff driven only by deterministic inputs (state + command + RNG seed).
 *
 * @property id Active contract identifier ([ActiveContractId]).
 * @property boardContractId Reference to the originating board contract ([ContractId]).
 * @property takenDay Day-index when the contract was taken (>= 0).
 * @property daysRemaining Remaining days until resolution (>= 0).
 * @property heroIds Participating hero identifiers ([HeroId]).
 * @property status Current active lifecycle status ([ActiveStatus]).
 */
data class ActiveContract(
    val id: ActiveContractId,
    val boardContractId: ContractId,
    val takenDay: Int,
    val daysRemaining: Int,
    val heroIds: List<HeroId>,
    val status: ActiveStatus
)

/**
 * Resolved contract result payload.
 *
 * ## Contract
 * Produced when an [ActiveContract] reaches resolution. A return may require explicit player close
 * (see [requiresPlayerClose]); otherwise it can be auto-closed by the reducer.
 *
 * ## Invariants
 * - [activeContractId] references an [ActiveContract.id] that has been resolved.
 * - [boardContractId] references the originating [BoardContract.id] on the board.
 * - [resolvedDay] is a day-index (expected >= 0).
 * - [trophiesCount] is a count (expected >= 0).
 * - [heroIds] are expected to reference existing heroes in [HeroState.roster].
 * - [requiresPlayerClose] implies the return must remain present until explicitly closed by a command.
 *
 * ## Determinism
 * Pure data container. Resolution content is deterministic iff resolution logic is deterministic given inputs (including RNG stream).
 *
 * @property activeContractId Identifier of the resolved active contract ([ActiveContractId]).
 * @property boardContractId Identifier of the originating board contract ([ContractId]).
 * @property heroIds Heroes participating in the resolved mission ([HeroId]).
 * @property resolvedDay Day-index when resolution occurred (>= 0).
 * @property outcome Mission outcome ([Outcome]).
 * @property trophiesCount Number of trophies reported/returned (count, >= 0).
 * @property trophiesQuality Trophy quality bucket ([Quality]).
 * @property reasonTags Diagnostic reason tags (stable string codes recommended; semantics are implementation-defined).
 * @property requiresPlayerClose Whether the player must explicitly close this return (e.g., partial outcomes).
 * @property suspectedTheft Whether trophy theft is suspected (e.g., based on trait-driven rules).
 */
data class ReturnPacket(
    val activeContractId: ActiveContractId,
    val boardContractId: ContractId,
    val heroIds: List<HeroId>,
    val resolvedDay: Int,
    val outcome: Outcome,
    val trophiesCount: Int,
    val trophiesQuality: Quality,
    val reasonTags: List<String>,
    val requiresPlayerClose: Boolean,
    val suspectedTheft: Boolean  // True if guild suspects hero stole trophies
)
