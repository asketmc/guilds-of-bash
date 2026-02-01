// FILE: core/src/main/kotlin/core/Reducer.kt
package core

import core.handlers.*
import core.invariants.verifyInvariants
import core.primitives.*
import core.rng.Rng
import core.state.*

/**
 * Domain transition boundary.
 *
 * Why:
 * - This file is the only place where gameplay state is allowed to change.
 * - It enforces replay-grade determinism. The same inputs must yield the same outputs.
 * - It emits an auditable event journal so adapters can render, test, and replay without inference.
 *
 * How:
 * - Every mutation is expressed as `Command -> step(...) -> StepResult`.
 * - Randomness is consumed only through the provided [Rng]. Draw order is part of the contract.
 * - Invariants are verified after the transition. Violations are surfaced as events, not hidden.
 */

// ─────────────────────────────────────────────────────────────────────────────
// Constants
// ─────────────────────────────────────────────────────────────────────────────

// (no file-level constants)

// ─────────────────────────────────────────────────────────────────────────────
// Data classes
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Result of a single command application.
 *
 * Why:
 * - [state] is the single source of truth for the world after the command.
 * - [events] is a strict, causally ordered explanation of why that [state] exists.
 *
 * How:
 * - [events] is emitted in a stable order and is safe to persist as a journal.
 * - A consumer must never rebuild [state] from [events] unless it is implementing a verified replay.
 */
data class StepResult(
    /** Authoritative state after the transition. */
    val state: GameState,
    /** Audit trail for adapters, analytics, and replay. */
    val events: List<Event>
)

// ─────────────────────────────────────────────────────────────────────────────
// SeqContext (encapsulated event accumulator)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Event sequencing policy.
 *
 * Why:
 * - Seq numbers must be monotonic within a step so event order is non-negotiable.
 * - Handlers must not reorder, backdate, or "fix" events outside explicit policy.
 *
 * How:
 * - [emit] assigns the next seq and appends in causal order.
 * - [insertBeforeDayEnded] exists only to keep end-of-day summaries terminal.
 * - [renumberSeqFrom1] re-derives seq after insertion; seq is derived from final order.
 */
class SeqContext {
    private val events = ArrayList<Event>(32)
    private var nextSeq = 1L

    /**
     * Why:
     * - Prevents any handler from emitting an event without a stable order position.
     *
     * How:
     * - Ignores any incoming seq and overwrites it with the next monotonic value.
     */
    fun emit(event: Event): Event {
        val withSeq = assignSeq(event, nextSeq++)
        events.add(withSeq)
        return withSeq
    }

    /**
     * Why:
     * - Exposes events to callers without granting mutation rights.
     *
     * How:
     * - Returns a defensive copy in emission order.
     */
    fun snapshot(): List<Event> = events.toList()

    /**
     * Why:
     * - Keeps [DayEnded] as the terminal domain event for a day when extra diagnostics are added.
     *
     * How:
     * - Inserts before the last element only when that element is [DayEnded].
     */
    fun insertBeforeDayEnded(toInsert: List<Event>) {
        val lastEventIsDayEnded = events.lastOrNull() is DayEnded
        val insertIndex = if (lastEventIsDayEnded) events.size - 1 else events.size
        events.addAll(insertIndex, toInsert)
    }

    /**
     * Why:
     * - Seq is not identity. Seq is the audit order.
     *
     * How:
     * - Rewrites every event with seq=1..N using the final list order.
     */
    fun renumberSeqFrom1() {
        for (i in events.indices) {
            events[i] = assignSeq(events[i], (i + 1).toLong())
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Helper functions
// ─────────────────────────────────────────────────────────────────────────────

private fun assignSeq(event: Event, seq: Long): Event {
    return when (event) {
        is DayStarted -> event.copy(seq = seq)
        is InboxGenerated -> event.copy(seq = seq)
        is HeroesArrived -> event.copy(seq = seq)
        is ContractPosted -> event.copy(seq = seq)
        is ContractTaken -> event.copy(seq = seq)
        is WipAdvanced -> event.copy(seq = seq)
        is ContractResolved -> event.copy(seq = seq)
        is ReturnClosed -> event.copy(seq = seq)
        is TrophySold -> event.copy(seq = seq)
        is StabilityUpdated -> event.copy(seq = seq)
        is DayEnded -> event.copy(seq = seq)
        is CommandRejected -> event.copy(seq = seq)
        is InvariantViolated -> event.copy(seq = seq)
        is HeroDeclined -> event.copy(seq = seq)
        is TrophyTheftSuspected -> event.copy(seq = seq)
        is TaxDue -> event.copy(seq = seq)
        is TaxPaid -> event.copy(seq = seq)
        is TaxMissed -> event.copy(seq = seq)
        is GuildShutdown -> event.copy(seq = seq)
        is GuildRankUp -> event.copy(seq = seq)
        is ProofPolicyChanged -> event.copy(seq = seq)
        is ContractDraftCreated -> event.copy(seq = seq)
        is ContractTermsUpdated -> event.copy(seq = seq)
        is ContractCancelled -> event.copy(seq = seq)
        is ContractAutoResolved -> event.copy(seq = seq)
        is HeroDied -> event.copy(seq = seq)
        is ReturnClosureBlocked -> event.copy(seq = seq)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Main reducer
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Deterministic state transition.
 *
 * Why:
 * - This is the single entry point for gameplay mutation.
 * - It is the enforcement point for validation, auditability, and invariant visibility.
 *
 * How:
 * - First validates [cmd]. On rejection: returns the unchanged [state] and one [CommandRejected] event.
 * - On acceptance: increments revision exactly once, then routes to a command handler.
 * - After the handler: verifies invariants and emits [InvariantViolated] events without rollback.
 * - Final event order is strict; seq is derived from that order.
 */
fun step(state: GameState, cmd: Command, rng: Rng): StepResult {
    val validationResult = canApply(state, cmd)
    if (validationResult is ValidationResult.Rejected) {
        var detailText = validationResult.detail
        if (validationResult.reason == RejectReason.INVALID_STATE) {
            if (!detailText.contains("money", ignoreCase = true) && !detailText.contains("escrow", ignoreCase = true)) {
                detailText = "$detailText (money/escrow)"
            }
        }


        // Use unified assignSeq for CommandRejected
        val event = assignSeq(
            CommandRejected(
                day = state.meta.dayIndex,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                cmdType = cmd::class.simpleName ?: "Unknown",
                reason = validationResult.reason,
                detail = detailText
            ),
            1L
        )
        return StepResult(state, listOf(event))
    }

    val stateWithRevision = state.copy(meta = state.meta.copy(revision = state.meta.revision + 1))
    val seqCtx = SeqContext()

    val newState = when (cmd) {
        is AdvanceDay -> handleAdvanceDay(stateWithRevision, cmd, rng, seqCtx)
        is PostContract -> handlePostContract(stateWithRevision, cmd, rng, seqCtx)
        is CloseReturn -> handleCloseReturn(stateWithRevision, cmd, rng, seqCtx)
        is SellTrophies -> handleSellTrophies(stateWithRevision, cmd, rng, seqCtx)
        is PayTax -> handlePayTax(stateWithRevision, cmd, rng, seqCtx)
        is SetProofPolicy -> handleSetProofPolicy(stateWithRevision, cmd, rng, seqCtx)
        is CreateContract -> handleCreateContract(stateWithRevision, cmd, rng, seqCtx)
        is UpdateContractTerms -> handleUpdateContractTerms(stateWithRevision, cmd, rng, seqCtx)
        is CancelContract -> handleCancelContract(stateWithRevision, cmd, rng, seqCtx)
    }

    val violations = verifyInvariants(newState)
    if (violations.isNotEmpty()) {
        val violationEvents = violations.map { v ->
            InvariantViolated(
                day = newState.meta.dayIndex,
                revision = newState.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                invariantId = v.invariantId,
                details = v.details
            )
        }
        seqCtx.insertBeforeDayEnded(violationEvents)
        seqCtx.renumberSeqFrom1()
    }

    return StepResult(newState, seqCtx.snapshot())
}

// ─────────────────────────────────────────────────────────────────────────────
// Command handlers (extracted to handlers/ package)
// ─────────────────────────────────────────────────────────────────────────────
// AdvanceDay: handlers/AdvanceDayHandler.kt
//   - handleAdvanceDay + all day phases (inbox, heroes, auto-resolve, pickup, WIP, tax, etc.)
// Contract commands: handlers/ContractHandlers.kt
//   - handlePostContract, handleCreateContract, handleUpdateContractTerms
//   - handleCancelContract, handleCloseReturn
// Economy commands: handlers/EconomyHandlers.kt
//   - handleSellTrophies, handlePayTax
// Governance commands: handlers/GovernanceHandlers.kt
//   - handleSetProofPolicy

