// FILE: core/src/main/kotlin/core/handlers/ContractHandlers.kt
package core.handlers

import core.*
import core.pipeline.*
import core.primitives.*
import core.rng.Rng
import core.state.*

/**
 * Contract command handlers.
 *
 * ## Semantic Ownership
 * Handles all contract lifecycle commands: create, post, update, cancel, close.
 *
 * ## Visibility
 * Internal to core module - only Reducer.kt should call these.
 *
 * ## Contract
 * - All handlers receive SeqContext for event emission
 * - All handlers return new GameState (never mutate)
 * - RNG is passed but may not be used by all handlers
 */

/**
 * Posts a draft contract from inbox to board.
 *
 * Why:
 * - Publication is the player's commitment point.
 * - Escrow exists to make commitment economically binding.
 *
 * How:
 * - Moves a draft from inbox to board.
 * - Reserves only the player's top-up portion; client deposit stays external to player funds.
 */
@Suppress("UNUSED_PARAMETER")
internal fun handlePostContract(
    state: GameState,
    cmd: PostContract,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val draft = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.inboxId } ?: return state

    val boardContract = BoardContract(
        id = draft.id,
        postedDay = state.meta.dayIndex,
        title = draft.title,
        rank = draft.rankSuggested,
        fee = cmd.fee,
        salvage = cmd.salvage,
        baseDifficulty = draft.baseDifficulty,
        status = BoardStatus.OPEN,
        clientDeposit = draft.clientDeposit
    )

    // Settlement: Economy delta for posting
    val economyDelta = EconomySettlement.computePostContractDelta(draft.clientDeposit)

    val newState = state.copy(
        contracts = state.contracts.copy(
            inbox = state.contracts.inbox.filter { it.id.value.toLong() != cmd.inboxId },
            board = state.contracts.board + boardContract
        ),
        economy = economyDelta.applyTo(state.economy)
    )

    ctx.emit(
        ContractPosted(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            boardContractId = boardContract.id.value,
            fromInboxId = draft.id.value,
            rank = draft.rankSuggested,
            fee = cmd.fee,
            salvage = cmd.salvage,
            clientDeposit = draft.clientDeposit
        )
    )

    return newState
}

/**
 * Creates a new contract draft in the inbox.
 *
 * Why:
 * - Contract authoring is a content injection point for tools and future adapters.
 *
 * How:
 * - Creates an inbox draft and advances id counters monotonically.
 */
@Suppress("UNUSED_PARAMETER")
internal fun handleCreateContract(
    state: GameState,
    cmd: CreateContract,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val newId = state.meta.ids.nextContractId
    // Use reward as clientDeposit (client's contribution towards the fee)
    val clientDeposit = cmd.reward.coerceAtLeast(0)

    val draft = ContractDraft(
        id = ContractId(newId),
        createdDay = state.meta.dayIndex,
        nextAutoResolveDay = state.meta.dayIndex + BalanceSettings.AUTO_RESOLVE_INTERVAL_DAYS,
        title = cmd.title,
        rankSuggested = cmd.rank,
        feeOffered = 0,
        salvage = cmd.salvage,
        baseDifficulty = cmd.difficulty,
        proofHint = "proof",
        clientDeposit = clientDeposit
    )

    ctx.emit(
        ContractDraftCreated(
            day = state.meta.dayIndex,
            revision = state.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            draftId = newId,
            title = cmd.title,
            rank = cmd.rank,
            difficulty = cmd.difficulty,
            reward = cmd.reward, // Emit original value for event compatibility
            salvage = cmd.salvage
        )
    )

    return state.copy(
        contracts = state.contracts.copy(inbox = state.contracts.inbox + draft),
        meta = state.meta.copy(ids = state.meta.ids.copy(nextContractId = newId + 1))
    )
}

/**
 * Updates contract terms in inbox or on board.
 *
 * Why:
 * - Negotiation is allowed before commitment.
 * - Terms changes must preserve escrow correctness.
 *
 * How:
 * - Applies updates either in inbox or on board.
 * - Adjusts reserved copper only by the player's top-up delta.
 */
@Suppress("UNUSED_PARAMETER")
internal fun handleUpdateContractTerms(
    state: GameState,
    cmd: UpdateContractTerms,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val inboxContract = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.contractId }
    val boardContract = state.contracts.board.firstOrNull { it.id.value.toLong() == cmd.contractId }

    return when {
        inboxContract != null -> {
            val feeApplied = cmd.newFee ?: inboxContract.feeOffered
            val salvageApplied = cmd.newSalvage ?: inboxContract.salvage

            val updatedDraft = inboxContract.copy(feeOffered = feeApplied, salvage = salvageApplied)

            ctx.emit(
                ContractTermsUpdated(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = inboxContract.id.value,
                    location = "inbox",
                    oldFee = inboxContract.feeOffered,
                    newFee = feeApplied,
                    oldSalvage = inboxContract.salvage,
                    newSalvage = salvageApplied
                )
            )

            state.copy(
                contracts = state.contracts.copy(
                    inbox = state.contracts.inbox.map { if (it.id.value.toLong() == cmd.contractId) updatedDraft else it }
                )
            )
        }

        boardContract != null -> {
            val oldFee = boardContract.fee
            val feeApplied = cmd.newFee ?: oldFee
            val salvageApplied = cmd.newSalvage ?: boardContract.salvage

            val updatedBoard = boardContract.copy(
                fee = feeApplied,
                salvage = salvageApplied
            )

            ctx.emit(
                ContractTermsUpdated(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = boardContract.id.value,
                    location = "board",
                    oldFee = oldFee,
                    newFee = feeApplied,
                    oldSalvage = boardContract.salvage,
                    newSalvage = salvageApplied
                )
            )

            state.copy(
                contracts = state.contracts.copy(
                    board = state.contracts.board.map { if (it.id.value.toLong() == cmd.contractId) updatedBoard else it }
                )
            )
        }

        else -> state
    }
}

/**
 * Cancels a contract from inbox or board.
 *
 * Why:
 * - Cancellation prevents dead contracts from blocking the board.
 * - Refund rules must match the escrow model.
 *
 * How:
 * - Inbox cancellation has no economic impact.
 * - Board cancellation refunds only the player's top-up portion.
 */
@Suppress("UNUSED_PARAMETER")
internal fun handleCancelContract(
    state: GameState,
    cmd: CancelContract,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val inboxContract = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.contractId }
    val boardContract = state.contracts.board.firstOrNull { it.id.value.toLong() == cmd.contractId }

    return when {
        inboxContract != null -> {
            ctx.emit(
                ContractCancelled(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = inboxContract.id.value,
                    location = "inbox",
                    refundedCopper = 0
                )
            )

            state.copy(contracts = state.contracts.copy(inbox = state.contracts.inbox.filter { it.id.value.toLong() != cmd.contractId }))
        }

        boardContract != null -> {
            // Settlement: Economy delta for cancellation
            val economyDelta = EconomySettlement.computeCancelContractDelta(boardContract.clientDeposit)

            ctx.emit(
                ContractCancelled(
                    day = state.meta.dayIndex,
                    revision = state.meta.revision,
                    cmdId = cmd.cmdId,
                    seq = 0L,
                    contractId = boardContract.id.value,
                    location = "board",
                    refundedCopper = boardContract.clientDeposit
                )
            )

            state.copy(
                contracts = state.contracts.copy(board = state.contracts.board.filter { it.id.value.toLong() != cmd.contractId }),
                economy = economyDelta.applyTo(state.economy)
            )
        }

        else -> state
    }
}

/**
 * Closes a completed return (manual player action).
 *
 * Why:
 * - Manual return closure is the player's governance moment.
 * - Strict proof is enforced by refusal to mutate state, not by rejection spam.
 *
 * How:
 * - In STRICT: silently no-ops on damaged proof or suspected theft.
 * - Otherwise: applies salvage, closes the active, releases escrow, and advances rank.
 */
@Suppress("UNUSED_PARAMETER", "ReturnCount")
internal fun handleCloseReturn(
    state: GameState,
    cmd: CloseReturn,
    rng: Rng,
    ctx: SeqContext
): GameState {
    val ret = state.contracts.returns.firstOrNull { it.activeContractId.value.toLong() == cmd.activeContractId } ?: return state
    val board = state.contracts.board.firstOrNull { it.id == ret.boardContractId }

    // Policy: Check if closure is allowed
    val closureCheck = ReturnClosurePolicy.canClose(
        state.guild.proofPolicy,
        ret.trophiesQuality,
        ret.suspectedTheft
    )
    if (!closureCheck.allowed) return state

    // Settlement: Hero lifecycle
    val updatedRoster = HeroLifecycle.computeManualCloseUpdate(
        ret.heroIds,
        state.heroes.roster
    )

    // Filter out this return and mark active as CLOSED
    val updatedReturns = state.contracts.returns.filter { it.activeContractId.value.toLong() != cmd.activeContractId }
    val updatedActives = state.contracts.active.map { active ->
        if (active.id.value.toLong() == cmd.activeContractId) active.copy(status = ActiveStatus.CLOSED) else active
    }

    // Settlement: Economy
    val economyDelta = EconomySettlement.computeManualCloseDelta(
        ret.outcome,
        board,
        ret.trophiesCount,
        ret.trophiesQuality,
        ret.suspectedTheft,
        state.economy
    )

    // Settlement: Board status
    val updatedBoard = BoardStatusModel.updateBoardStatus(
        boards = state.contracts.board,
        boardIdToComplete = board?.id,
        activeContracts = updatedActives
    )

    // Settlement: Guild progression
    val guildResult = GuildProgression.computeAfterCompletion(
        state.guild.completedContractsTotal,
        state.guild.guildRank
    )

    if (guildResult.rankChanged) {
        ctx.emit(
            GuildRankUp(
                day = state.meta.dayIndex,
                revision = state.meta.revision,
                cmdId = cmd.cmdId,
                seq = 0L,
                oldRank = guildResult.oldRank,
                newRank = guildResult.newGuildRank,
                completedContracts = guildResult.newCompletedContractsTotal
            )
        )
    }

    // Apply all settlements
    val newState = state.copy(
        contracts = state.contracts.copy(
            board = updatedBoard,
            active = updatedActives,
            returns = updatedReturns
        ),
        heroes = state.heroes.copy(roster = updatedRoster),
        economy = economyDelta.applyTo(state.economy),
        guild = state.guild.copy(
            completedContractsTotal = guildResult.newCompletedContractsTotal,
            contractsForNextRank = guildResult.contractsForNextRank,
            guildRank = guildResult.newGuildRank
        )
    )

    ctx.emit(
        ReturnClosed(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            cmdId = cmd.cmdId,
            seq = 0L,
            activeContractId = cmd.activeContractId.toInt()
        )
    )

    return newState
}
