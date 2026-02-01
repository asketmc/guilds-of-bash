package core

import core.invariants.InvariantId
import core.primitives.AutoResolveBucket
import core.primitives.Outcome
import core.primitives.Quality
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.primitives.ProofPolicy

/**
 * Domain events are the ONLY observation channel for external consumers.
 *
 * ## Role
 * - Every state mutation emits events that fully explain the transition.
 * - Events form an auditable journal for replay, testing, and analytics.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/analytics
 *
 * ## Invariants
 * - `seq` is monotonically increasing within a single `step()` call (1..N).
 * - `day` reflects the authoritative `dayIndex` at emission time.
 * - `revision` matches the state revision when the event was produced.
 *
 * ## DAY SEMANTICS
 * - `AdvanceDay` increments `state.meta.dayIndex` first.
 * - All events emitted for `AdvanceDay` use `day = newDayIndex`.
 *
 * @property day Day index when this event was emitted.
 * @property revision State revision at emission time.
 * @property cmdId Command identifier that triggered this event.
 * @property seq Sequence number within the step (1..N, ascending).
 */
sealed interface Event {
    val day: Int
    val revision: Long
    val cmdId: Long
    val seq: Long
}

/**
 * End-of-day summary snapshot of key game metrics.
 *
 * Uses only primitive values for stable hashing and cross-platform portability.
 *
 * @property day Day index of this snapshot.
 * @property revision State revision at snapshot time.
 * @property money Guild copper balance.
 * @property trophies Guild trophy inventory count.
 * @property regionStability Current regional stability (0..100).
 * @property guildReputation Guild reputation points.
 * @property inboxCount Number of drafts in inbox.
 * @property boardCount Number of contracts on board.
 * @property activeCount Number of WIP active contracts.
 * @property returnsNeedingCloseCount Returns requiring manual player close.
 */
data class DaySnapshot(
    val day: Int,
    val revision: Long,
    val money: Int,
    val trophies: Int,
    val regionStability: Int,
    val guildReputation: Int,
    val inboxCount: Int,
    val boardCount: Int,
    val activeCount: Int,
    val returnsNeedingCloseCount: Int
)

/**
 * Emitted when a new day begins.
 *
 * First event in the `AdvanceDay` pipeline; marks the transition to `newDayIndex`.
 */
data class DayStarted(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long
) : Event

/**
 * Emitted when new contract drafts are generated for the inbox.
 *
 * @property count Number of drafts generated.
 * @property contractIds Array of generated contract IDs (sorted ascending).
 */
data class InboxGenerated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val count: Int,
    val contractIds: IntArray
) : Event {
    /**
     * ## Contract
     * - Structural equality for [InboxGenerated] is based on all scalar fields plus element-wise
     *   equality of [contractIds].
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Returns `true` iff [other] is an [InboxGenerated] with equal `day`, `revision`, `cmdId`, `seq`, `count`,
     *   and `contractIds` contents.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Pure comparison against immutable values.
     *
     * ## Complexity
     * - Time: O(n) where n = `contractIds.size`
     * - Memory: O(1)
     *
     * @param other Candidate object to compare.
     * @return Whether objects are equal under the above contract.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as InboxGenerated
        if (day != other.day) return false
        if (revision != other.revision) return false
        if (cmdId != other.cmdId) return false
        if (seq != other.seq) return false
        if (count != other.count) return false
        if (!contractIds.contentEquals(other.contractIds)) return false
        return true
    }

    /**
     * ## Contract
     * - Hash code includes element-wise hashing for [contractIds] to keep equality/hashCode consistent.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Equal objects (per [equals]) produce equal hash codes.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Pure function over immutable values.
     *
     * ## Complexity
     * - Time: O(n) where n = `contractIds.size`
     * - Memory: O(1)
     *
     * @return Hash code for this value.
     */
    override fun hashCode(): Int {
        var result = day
        result = 31 * result + revision.hashCode()
        result = 31 * result + cmdId.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + count
        result = 31 * result + contractIds.contentHashCode()
        return result
    }

    /**
     * ## Contract
     * - Produces a deterministic diagnostic string for logs/tests.
     * - Intended for debugging and golden-output comparisons; not a stable public protocol.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Returned string includes all fields, including `contractIds` rendered in-order.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Deterministic given immutable fields and fixed `contentToString()` order.
     *
     * ## Complexity
     * - Time: O(n) where n = `contractIds.size`
     * - Memory: O(n) due to string construction
     *
     * @return Human-readable representation.
     */
    override fun toString(): String {
        return "InboxGenerated(day=$day, revision=$revision, cmdId=$cmdId, seq=$seq, count=$count, contractIds=${contractIds.contentToString()})"
    }
}

/**
 * Emitted when a draft contract is posted to the public board.
 *
 * @property boardContractId ID of the newly posted board contract.
 * @property fromInboxId ID of the source inbox draft.
 * @property rank Target difficulty tier.
 * @property fee Escrowed fee in copper.
 * @property salvage Trophy distribution policy.
 * @property clientDeposit Client's contribution towards the fee.
 */
data class ContractPosted(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val boardContractId: Int,
    val fromInboxId: Int,
    val rank: Rank,
    val fee: Int,
    val salvage: SalvagePolicy,
    val clientDeposit: Int = 0
) : Event

/**
 * Emitted when new heroes arrive at the guild.
 *
 * @property count Number of heroes arrived.
 * @property heroIds Array of arrived hero IDs (sorted ascending).
 */
data class HeroesArrived(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val count: Int,
    val heroIds: IntArray // sorted ascending
) : Event {
    /**
     * ## Contract
     * - Structural equality is based on all scalar fields plus element-wise equality of [heroIds].
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Returns `true` iff [other] is a [HeroesArrived] with equal fields and equal `heroIds` contents.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Pure comparison against immutable values.
     *
     * ## Complexity
     * - Time: O(n) where n = `heroIds.size`
     * - Memory: O(1)
     *
     * @param other Candidate object to compare.
     * @return Whether objects are equal under the above contract.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as HeroesArrived
        if (day != other.day) return false
        if (revision != other.revision) return false
        if (cmdId != other.cmdId) return false
        if (seq != other.seq) return false
        if (count != other.count) return false
        if (!heroIds.contentEquals(other.heroIds)) return false
        return true
    }

    /**
     * ## Contract
     * - Hash code includes element-wise hashing for [heroIds] to keep equality/hashCode consistent.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Equal objects (per [equals]) produce equal hash codes.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Pure function over immutable values.
     *
     * ## Complexity
     * - Time: O(n) where n = `heroIds.size`
     * - Memory: O(1)
     *
     * @return Hash code for this value.
     */
    override fun hashCode(): Int {
        var result = day
        result = 31 * result + revision.hashCode()
        result = 31 * result + cmdId.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + count
        result = 31 * result + heroIds.contentHashCode()
        return result
    }

    /**
     * ## Contract
     * - Produces a deterministic diagnostic string for logs/tests.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Returned string includes all fields, including `heroIds` rendered in-order.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Deterministic given immutable fields and fixed `contentToString()` order.
     *
     * ## Complexity
     * - Time: O(n) where n = `heroIds.size`
     * - Memory: O(n) due to string construction
     *
     * @return Human-readable representation.
     */
    override fun toString(): String {
        return "HeroesArrived(day=$day, revision=$revision, cmdId=$cmdId, seq=$seq, count=$count, heroIds=${heroIds.contentToString()})"
    }
}

/**
 * Emitted when a hero accepts a board contract and begins the mission.
 *
 * @property activeContractId ID of the newly created active contract.
 * @property boardContractId ID of the board contract taken.
 * @property heroIds Array of participating hero IDs (sorted ascending).
 * @property daysRemaining Initial days until resolution.
 */
data class ContractTaken(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int,
    val boardContractId: Int,
    val heroIds: IntArray, // sorted ascending
    val daysRemaining: Int
) : Event {
    /**
     * ## Contract
     * - Structural equality is based on all scalar fields plus element-wise equality of [heroIds].
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Returns `true` iff [other] is a [ContractTaken] with equal fields and equal `heroIds` contents.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Pure comparison against immutable values.
     *
     * ## Complexity
     * - Time: O(n) where n = `heroIds.size`
     * - Memory: O(1)
     *
     * @param other Candidate object to compare.
     * @return Whether objects are equal under the above contract.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ContractTaken
        if (day != other.day) return false
        if (revision != other.revision) return false
        if (cmdId != other.cmdId) return false
        if (seq != other.seq) return false
        if (activeContractId != other.activeContractId) return false
        if (boardContractId != other.boardContractId) return false
        if (!heroIds.contentEquals(other.heroIds)) return false
        if (daysRemaining != other.daysRemaining) return false
        return true
    }

    /**
     * ## Contract
     * - Hash code includes element-wise hashing for [heroIds] to keep equality/hashCode consistent.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Equal objects (per [equals]) produce equal hash codes.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Pure function over immutable values.
     *
     * ## Complexity
     * - Time: O(n) where n = `heroIds.size`
     * - Memory: O(1)
     *
     * @return Hash code for this value.
     */
    override fun hashCode(): Int {
        var result = day
        result = 31 * result + revision.hashCode()
        result = 31 * result + cmdId.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + activeContractId
        result = 31 * result + boardContractId
        result = 31 * result + heroIds.contentHashCode()
        result = 31 * result + daysRemaining
        return result
    }

    /**
     * ## Contract
     * - Produces a deterministic diagnostic string for logs/tests.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Returned string includes all fields, including `heroIds` rendered in-order.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Deterministic given immutable fields and fixed `contentToString()` order.
     *
     * ## Complexity
     * - Time: O(n) where n = `heroIds.size`
     * - Memory: O(n) due to string construction
     *
     * @return Human-readable representation.
     */
    override fun toString(): String {
        return "ContractTaken(day=$day, revision=$revision, cmdId=$cmdId, seq=$seq, activeContractId=$activeContractId, boardContractId=$boardContractId, heroIds=${heroIds.contentToString()}, daysRemaining=$daysRemaining)"
    }
}

/**
 * Emitted when an active contract's remaining days are decremented.
 *
 * @property activeContractId ID of the advancing active contract.
 * @property daysRemaining New remaining days after advancement.
 */
data class WipAdvanced(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int,
    val daysRemaining: Int
) : Event

/**
 * Emitted when a contract reaches resolution (SUCCESS, PARTIAL, FAIL, DEATH, MISSING).
 *
 * @property activeContractId ID of the resolved active contract.
 * @property outcome Resolution outcome.
 * @property trophiesCount Trophies reported (after theft processing).
 * @property quality Trophy quality grade.
 * @property reasonTags Diagnostic reason tag ordinals (sorted ascending).
 */
data class ContractResolved(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int,
    val outcome: Outcome,
    val trophiesCount: Int,
    val quality: Quality,
    val reasonTags: IntArray // ordinals, sorted ascending
) : Event {
    /**
     * ## Contract
     * - Structural equality for [ContractResolved] is based on all scalar fields plus element-wise
     *   equality of [reasonTags].
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Returns `true` iff [other] is a [ContractResolved] with equal fields and equal `reasonTags` contents.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Pure comparison against immutable values.
     *
     * ## Complexity
     * - Time: O(n) where n = `reasonTags.size`
     * - Memory: O(1)
     *
     * @param other Candidate object to compare.
     * @return Whether objects are equal under the above contract.
     */
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as ContractResolved
        if (day != other.day) return false
        if (revision != other.revision) return false
        if (cmdId != other.cmdId) return false
        if (seq != other.seq) return false
        if (activeContractId != other.activeContractId) return false
        if (outcome != other.outcome) return false
        if (trophiesCount != other.trophiesCount) return false
        if (quality != other.quality) return false
        if (!reasonTags.contentEquals(other.reasonTags)) return false
        return true
    }

    /**
     * ## Contract
     * - Hash code includes element-wise hashing for [reasonTags] to keep equality/hashCode consistent.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Equal objects (per [equals]) produce equal hash codes.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Pure function over immutable values.
     *
     * ## Complexity
     * - Time: O(n) where n = `reasonTags.size`
     * - Memory: O(1)
     *
     * @return Hash code for this value.
     */
    override fun hashCode(): Int {
        var result = day
        result = 31 * result + revision.hashCode()
        result = 31 * result + cmdId.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + activeContractId
        result = 31 * result + outcome.hashCode()
        result = 31 * result + trophiesCount
        result = 31 * result + quality.hashCode()
        result = 31 * result + reasonTags.contentHashCode()
        return result
    }

    /**
     * ## Contract
     * - Produces a deterministic diagnostic string for logs/tests.
     *
     * ## Preconditions
     * - None.
     *
     * ## Postconditions
     * - Returned string includes all fields, including `reasonTags` rendered in-order.
     *
     * ## Invariants
     * - None.
     *
     * ## Determinism
     * - Deterministic given immutable fields and fixed `contentToString()` order.
     *
     * ## Complexity
     * - Time: O(n) where n = `reasonTags.size`
     * - Memory: O(n) due to string construction
     *
     * @return Human-readable representation.
     */
    override fun toString(): String {
        return "ContractResolved(day=$day, revision=$revision, cmdId=$cmdId, seq=$seq, activeContractId=$activeContractId, outcome=$outcome, trophiesCount=$trophiesCount, quality=$quality, reasonTags=${reasonTags.contentToString()})"
    }
}

/**
 * Emitted when a return is closed (either manually or automatically).
 *
 * @property activeContractId ID of the closed active contract.
 */
data class ReturnClosed(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int
) : Event

/**
 * Emitted when the player sells trophies for copper.
 *
 * @property amount Number of trophies sold.
 * @property moneyGained Copper received from sale.
 */
data class TrophySold(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amount: Int,
    val moneyGained: Int
) : Event

/**
 * Emitted when regional stability changes.
 *
 * @property oldStability Previous stability value.
 * @property newStability Updated stability value.
 */
data class StabilityUpdated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val oldStability: Int,
    val newStability: Int
) : Event

/**
 * Emitted at the end of each day with a summary snapshot.
 *
 * Terminal event for `AdvanceDay` command.
 *
 * @property snapshot End-of-day metrics snapshot.
 */
data class DayEnded(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val snapshot: DaySnapshot
) : Event

/**
 * Emitted when a command fails validation.
 *
 * @property cmdType Simple name of the rejected command class.
 * @property reason Machine-readable rejection code.
 * @property detail Human-readable diagnostic message.
 */
data class CommandRejected(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val cmdType: String,
    val reason: RejectReason,
    val detail: String
) : Event

/**
 * Emitted when a state invariant is violated.
 *
 * Violations are surfaced as events for diagnostics; state is not rolled back.
 *
 * @property invariantId Identifier of the violated invariant.
 * @property details Diagnostic description of the violation.
 */
data class InvariantViolated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val invariantId: InvariantId,
    val details: String
) : Event

/**
 * Emitted when a hero declines a contract during pickup phase.
 *
 * @property heroId ID of the declining hero.
 * @property boardContractId ID of the declined board contract.
 * @property reason Decline reason code (e.g., "low_profit", "too_risky").
 */
data class HeroDeclined(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val heroId: Int,
    val boardContractId: Int,
    val reason: String
) : Event

/**
 * Emitted when trophy theft is suspected based on hero traits.
 *
 * @property activeContractId ID of the affected active contract.
 * @property heroId ID of the suspected hero.
 * @property expectedTrophies Expected trophy count based on contract difficulty.
 * @property reportedTrophies Actual trophy count reported by hero.
 */
data class TrophyTheftSuspected(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int,
    val heroId: Int,
    val expectedTrophies: Int,
    val reportedTrophies: Int
) : Event

/**
 * Emitted when tax becomes due at the end of a tax interval.
 *
 * @property amountDue Tax amount owed in copper.
 * @property dueDay Day index when payment is expected.
 */
data class TaxDue(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountDue: Int,
    val dueDay: Int
) : Event

/**
 * Emitted when the player pays tax.
 *
 * @property amountPaid Copper amount paid.
 * @property amountDue Remaining amount due after payment.
 * @property isPartialPayment True if debt remains after payment.
 */
data class TaxPaid(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountPaid: Int,
    val amountDue: Int,
    val isPartialPayment: Boolean
) : Event

/**
 * Emitted when tax payment is missed at end of day.
 *
 * @property amountDue Total amount that was due.
 * @property penaltyAdded Penalty copper added for missing payment.
 * @property missedCount New consecutive missed payment count.
 */
data class TaxMissed(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountDue: Int,
    val penaltyAdded: Int,
    val missedCount: Int
) : Event

/**
 * Emitted when the guild is shut down (e.g., too many missed tax payments).
 *
 * @property reason Description of shutdown cause.
 */
data class GuildShutdown(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val reason: String
) : Event

/**
 * Emitted when the guild achieves a new rank.
 *
 * @property oldRank Previous rank ordinal.
 * @property newRank New rank ordinal achieved.
 * @property completedContracts Total completed contracts at rank-up.
 */
data class GuildRankUp(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val oldRank: Int,
    val newRank: Int,
    val completedContracts: Int
) : Event

/**
 * Emitted when the guild's proof policy changes.
 *
 * @property oldPolicy Previous policy ordinal.
 * @property newPolicy New policy ordinal.
 */
data class ProofPolicyChanged(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val oldPolicy: Int,
    val newPolicy: Int
) : Event

/**
 * Emitted when a new contract draft is created via `CreateContract` command.
 *
 * @property draftId ID of the created draft.
 * @property title Contract title.
 * @property rank Target difficulty tier.
 * @property difficulty Base difficulty value.
 * @property reward Client contribution towards fee.
 * @property salvage Default trophy distribution policy.
 */
data class ContractDraftCreated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val draftId: Int,
    val title: String,
    val rank: Rank,
    val difficulty: Int,
    val reward: Int,
    val salvage: SalvagePolicy
) : Event

/**
 * Emitted when contract terms are updated via `UpdateContractTerms` command.
 *
 * @property contractId ID of the updated contract.
 * @property location Where the contract was found ("inbox" or "board").
 * @property oldFee Previous fee value (null if unchanged).
 * @property newFee New fee value (null if unchanged).
 * @property oldSalvage Previous salvage policy (null if unchanged).
 * @property newSalvage New salvage policy (null if unchanged).
 */
data class ContractTermsUpdated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val contractId: Int,
    val location: String,
    val oldFee: Int?,
    val newFee: Int?,
    val oldSalvage: SalvagePolicy?,
    val newSalvage: SalvagePolicy?
) : Event

/**
 * Emitted when a contract is cancelled via `CancelContract` command.
 *
 * @property contractId ID of the cancelled contract.
 * @property location Where the contract was found ("inbox" or "board").
 * @property refundedCopper Copper refunded (client deposit for board contracts).
 */
data class ContractCancelled(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val contractId: Int,
    val location: String,
    val refundedCopper: Int
) : Event

/**
 * Emitted when an inbox draft is auto-resolved due to expiration.
 *
 * @property draftId ID of the auto-resolved draft.
 * @property bucket Resolution bucket (GOOD, NEUTRAL, BAD).
 */
data class ContractAutoResolved(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val draftId: Int,
    val bucket: AutoResolveBucket
) : Event

/**
 * Emitted when a hero dies during contract resolution.
 *
 * @property heroId ID of the deceased hero.
 * @property activeContractId ID of the active contract during death.
 * @property boardContractId ID of the board contract.
 */
data class HeroDied(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val heroId: Int,
    val activeContractId: Int,
    val boardContractId: Int
) : Event

/**
 * Emitted when a manual return closure (CloseReturn) is blocked by policy.
 *
 * ## Purpose
 * STRICT policy currently denies closure for certain proof outcomes. This event preserves the
 * existing behavior (state unchanged) while making the decision observable for logs, tests,
 * adapters, and future dispute/arbitration mechanics.
 *
 * ## Determinism
 * - No RNG draws.
 * - Purely derived from current state and command inputs.
 *
 * @property activeContractId Active contract id for the return the player attempted to close.
 * @property policy Proof policy that blocked closure.
 * @property reason Machine-readable reason tag (see ReturnClosurePolicy).
 */
data class ReturnClosureBlocked(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int,
    val policy: ProofPolicy,
    val reason: String
) : Event

// ─────────────────────────────────────────────────────────────────────────────
// Fraud Investigation Events (v0)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Emitted when a fraud candidate is investigated.
 *
 * ## Purpose
 * Records the outcome of a fraud investigation triggered by `suspectedTheft == true`.
 * This event makes the investigation pipeline observable and auditable.
 *
 * ## Pipeline Position
 * Emitted during contract resolution phase when processing returns with suspected theft.
 *
 * ## Determinism
 * - RNG draws: 1 for investigation roll, optionally 1 for rumor roll if not caught.
 * - Order: by activeContractId (ascending).
 *
 * @property heroId ID of the investigated hero.
 * @property activeContractId ID of the active contract that triggered investigation.
 * @property policy Proof policy in effect during investigation.
 * @property caught Whether the hero was caught (true = warn/ban applied).
 * @property rumorScheduled Whether a rumor was scheduled (only when not caught).
 */
data class FraudInvestigated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val heroId: Int,
    val activeContractId: Int,
    val policy: ProofPolicy,
    val caught: Boolean,
    val rumorScheduled: Boolean
) : Event

/**
 * Emitted when a hero receives a WARN status for first-time fraud.
 *
 * ## Purpose
 * Records the application of a WARN penalty to a hero caught for fraud.
 * The hero can still take contracts but a repeat offense leads to BAN.
 *
 * ## Determinism
 * - No RNG draws.
 * - Purely derived from investigation outcome.
 *
 * @property heroId ID of the warned hero.
 * @property untilDay Day index when the WARN expires.
 * @property reason Machine-readable reason for the warning.
 */
data class HeroWarned(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val heroId: Int,
    val untilDay: Int,
    val reason: String
) : Event

/**
 * Emitted when a hero receives a BAN status for repeated fraud.
 *
 * ## Purpose
 * Records the application of a BAN penalty to a hero caught for fraud while already warned.
 * Banned heroes cannot take contracts until the ban expires.
 *
 * ## Determinism
 * - No RNG draws.
 * - Purely derived from investigation outcome.
 *
 * @property heroId ID of the banned hero.
 * @property untilDay Day index when the BAN expires.
 * @property reason Machine-readable reason for the ban.
 */
data class HeroBanned(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val heroId: Int,
    val untilDay: Int,
    val reason: String
) : Event

/**
 * Emitted when a fraud rumor is scheduled due to escaped fraud.
 *
 * ## Purpose
 * Records the scheduling of a reputation penalty from a fraud rumor.
 * The penalty is accumulated in `pendingReputationDelta` and applied on weekly boundary.
 *
 * ## Determinism
 * - No RNG draws (the rumor roll is part of FraudInvestigated).
 * - Purely derived from investigation outcome.
 *
 * @property policy Proof policy that allowed the fraud to escape.
 * @property repDeltaPlanned Planned reputation penalty (negative).
 */
data class RumorScheduled(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val policy: ProofPolicy,
    val repDeltaPlanned: Int
) : Event

/**
 * Emitted when the weekly report is published and pending reputation changes are applied.
 *
 * ## Purpose
 * Records the application of accumulated reputation changes from rumors and other sources.
 * Provides a summary of fraud-related activity for the week.
 *
 * ## Pipeline Position
 * Emitted on weekly boundary (when dayIndex is a multiple of TAX_INTERVAL_DAYS).
 *
 * ## Determinism
 * - No RNG draws.
 * - Purely derived from accumulated state.
 *
 * @property reputationDeltaApplied Total reputation change applied.
 * @property rumorsCount Number of rumors that contributed to the delta.
 * @property bansCount Number of bans issued this week.
 * @property warnsCount Number of warns issued this week.
 */
data class WeeklyReportPublished(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val reputationDeltaApplied: Int,
    val rumorsCount: Int,
    val bansCount: Int,
    val warnsCount: Int
) : Event

