package core

import core.invariants.InvariantId
import core.primitives.AutoResolveBucket
import core.primitives.Outcome
import core.primitives.Quality
import core.primitives.Rank
import core.primitives.SalvagePolicy

/**
 * Events are the ONLY observation channel for the outside world.
 * - seq is a per-step monotonically increasing counter (1..N within a step).
 * - step() is responsible for assigning seq.
 *
 * DAY SEMANTICS:
 * - AdvanceDay increments state.meta.dayIndex first.
 * - All events emitted for AdvanceDay use day = newDayIndex.
 */
sealed interface Event {
    val day: Int
    val revision: Long
    val cmdId: Long
    val seq: Long
}

/**
 * A minimal snapshot of the state at the end of a day.
 * Uses only primitive values for stable hashing and portability.
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

data class DayStarted(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long
) : Event

data class InboxGenerated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val count: Int,
    val contractIds: IntArray // sorted ascending
) : Event {
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

    override fun hashCode(): Int {
        var result = day
        result = 31 * result + revision.hashCode()
        result = 31 * result + cmdId.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + count
        result = 31 * result + contractIds.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "InboxGenerated(day=$day, revision=$revision, cmdId=$cmdId, seq=$seq, count=$count, contractIds=${contractIds.contentToString()})"
    }
}

data class ContractPosted(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val boardContractId: Int,
    val fromInboxId: Int,
    val rank: Rank,
    val fee: Int,
    val salvage: SalvagePolicy
) : Event

data class HeroesArrived(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val count: Int,
    val heroIds: IntArray // sorted ascending
) : Event {
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

    override fun hashCode(): Int {
        var result = day
        result = 31 * result + revision.hashCode()
        result = 31 * result + cmdId.hashCode()
        result = 31 * result + seq.hashCode()
        result = 31 * result + count
        result = 31 * result + heroIds.contentHashCode()
        return result
    }

    override fun toString(): String {
        return "HeroesArrived(day=$day, revision=$revision, cmdId=$cmdId, seq=$seq, count=$count, heroIds=${heroIds.contentToString()})"
    }
}

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

    override fun toString(): String {
        return "ContractTaken(day=$day, revision=$revision, cmdId=$cmdId, seq=$seq, activeContractId=$activeContractId, boardContractId=$boardContractId, heroIds=${heroIds.contentToString()}, daysRemaining=$daysRemaining)"
    }
}

data class WipAdvanced(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int,
    val daysRemaining: Int
) : Event

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

    override fun toString(): String {
        return "ContractResolved(day=$day, revision=$revision, cmdId=$cmdId, seq=$seq, activeContractId=$activeContractId, outcome=$outcome, trophiesCount=$trophiesCount, quality=$quality, reasonTags=${reasonTags.contentToString()})"
    }
}

data class ReturnClosed(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int
) : Event

data class TrophySold(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amount: Int,
    val moneyGained: Int
) : Event

data class StabilityUpdated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val oldStability: Int,
    val newStability: Int
) : Event

data class DayEnded(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val snapshot: DaySnapshot
) : Event

data class CommandRejected(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val cmdType: String,
    val reason: RejectReason,
    val detail: String
) : Event

data class InvariantViolated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val invariantId: InvariantId,
    val details: String
) : Event

data class HeroDeclined(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val heroId: Int,
    val boardContractId: Int,
    val reason: String  // "low_profit", "too_risky", "bad_terms", etc.
) : Event

data class TrophyTheftSuspected(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val activeContractId: Int,
    val heroId: Int,
    val expectedTrophies: Int,  // What guild expected based on contract difficulty
    val reportedTrophies: Int   // What hero actually reported
) : Event

// Tax events (Phase 2)
data class TaxDue(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountDue: Int,
    val dueDay: Int
) : Event

data class TaxPaid(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountPaid: Int,
    val amountDue: Int,
    val isPartialPayment: Boolean
) : Event

data class TaxMissed(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val amountDue: Int,
    val penaltyAdded: Int,
    val missedCount: Int
) : Event

data class GuildShutdown(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val reason: String
) : Event

data class GuildRankUp(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val oldRank: Int,
    val newRank: Int,
    val completedContracts: Int
) : Event

data class ProofPolicyChanged(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val oldPolicy: Int,
    val newPolicy: Int
) : Event

// R1: Lifecycle command events
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

data class ContractTermsUpdated(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val contractId: Int,
    val location: String,  // "inbox" or "board"
    val oldFee: Int?,
    val newFee: Int?,
    val oldSalvage: SalvagePolicy?,
    val newSalvage: SalvagePolicy?
) : Event

data class ContractCancelled(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val contractId: Int,
    val location: String,  // "inbox" or "board"
    val refundedCopper: Int
) : Event

data class ContractAutoResolved(
    override val day: Int,
    override val revision: Long,
    override val cmdId: Long,
    override val seq: Long,
    val draftId: Int,
    val bucket: AutoResolveBucket
) : Event

