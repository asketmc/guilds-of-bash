// FILE: core/src/main/kotlin/core/CommandValidation.kt
package core

import core.primitives.ActiveStatus
import core.primitives.BoardStatus
import core.state.GameState

/**
 * Machine-readable error codes for command validation failures.
 */
enum class RejectReason {
    NOT_FOUND,
    INVALID_ARG,
    INVALID_STATE
}

/**
 * Sealed result type representing validation outcomes.
 */
sealed interface ValidationResult {
    /**
     * Command passed all validation checks.
     */
    data object Valid : ValidationResult

    /**
     * Command failed validation.
     *
     * @property reason Stable machine-readable error code.
     * @property detail Human-readable diagnostic message (non-authoritative).
     */
    data class Rejected(
        val reason: RejectReason,
        val detail: String
    ) : ValidationResult
}

/**
 * Validates whether a command can be applied to the given game state.
 *
 * Complexity: O(n) where n is the size of the relevant collection being searched.
 *
 * @param state Current game state (immutable, not modified).
 * @param cmd Command to validate.
 * @return ValidationResult.Valid if command is valid, ValidationResult.Rejected otherwise.
 *
 * Side effects: None (pure function).
 */
fun canApply(state: GameState, cmd: Command): ValidationResult {
    // Global check: cmdId must be positive
    if (cmd.cmdId <= 0L) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_ARG,
            detail = "cmdId=${cmd.cmdId} must be positive"
        )
    }

    return when (cmd) {
        is PostContract -> validatePostContract(state, cmd)
        is CloseReturn -> validateCloseReturn(state, cmd)
        is SellTrophies -> validateSellTrophies(state, cmd)
        is PayTax -> validatePayTax(state, cmd)
        is SetProofPolicy -> ValidationResult.Valid
        is AdvanceDay -> ValidationResult.Valid
        is CreateContract -> validateCreateContract(state, cmd)
        is UpdateContractTerms -> validateUpdateContractTerms(state, cmd)
        is CancelContract -> validateCancelContract(state, cmd)
    }
}

/**
 * Validates PostContract command.
 *
 * Complexity: O(n) where n = state.contracts.inbox.size.
 *
 * Input invariants:
 * - cmd.inboxId must reference an existing inbox contract.
 * - cmd.fee must be non-negative.
 *
 * Output contract:
 * - Returns Valid if inboxId exists and fee >= 0.
 * - Returns Rejected(NOT_FOUND) if inboxId not found.
 * - Returns Rejected(INVALID_ARG) if fee < 0.
 *
 * Side effects: None.
 */
@Suppress("ReturnCount")
private fun validatePostContract(state: GameState, cmd: PostContract): ValidationResult {
    val inboxExists = state.contracts.inbox.any { it.id.value.toLong() == cmd.inboxId }
    if (!inboxExists) {
        return ValidationResult.Rejected(
            reason = RejectReason.NOT_FOUND,
            detail = "inboxId=${cmd.inboxId} not found"
        )
    }
    if (cmd.fee < 0) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_ARG,
            detail = "fee=${cmd.fee} must be >= 0"
        )
    }
    val availableCopper = state.economy.moneyCopper - state.economy.reservedCopper
    if (availableCopper < cmd.fee) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_STATE,
            detail = "Insufficient available funds: need ${cmd.fee}, available ${availableCopper}"
        )
    }
    return ValidationResult.Valid
}

/**
 * Validates CloseReturn command.
 *
 * Complexity: O(m + n) where m = state.contracts.active.size, n = state.contracts.returns.size.
 *
 * Input invariants:
 * - cmd.activeContractId must reference an existing active contract.
 * - Referenced contract must have status RETURN_READY.
 * - A return record must exist with requiresPlayerClose=true.
 *
 * Output contract:
 * - Returns Valid if all checks pass.
 * - Returns Rejected(NOT_FOUND) if contract or return not found.
 * - Returns Rejected(INVALID_STATE) if status != RETURN_READY.
 *
 * Side effects: None.
 */
@Suppress("ReturnCount")
private fun validateCloseReturn(state: GameState, cmd: CloseReturn): ValidationResult {
    val returnRequiresClose = state.contracts.returns.any {
        it.activeContractId.value.toLong() == cmd.activeContractId && it.requiresPlayerClose
    }
    if (!returnRequiresClose) {
        return ValidationResult.Rejected(
            reason = RejectReason.NOT_FOUND,
            detail = "return for activeContractId=${cmd.activeContractId} not found or does not require close"
        )
    }

    val ret = state.contracts.returns.firstOrNull { it.activeContractId.value.toLong() == cmd.activeContractId }
        ?: return ValidationResult.Rejected(
            reason = RejectReason.NOT_FOUND,
            detail = "return for activeContractId=${cmd.activeContractId} not found"
        )

    val board = state.contracts.board.firstOrNull { it.id == ret.boardContractId }
    val fee = board?.fee ?: 0
    if (state.economy.reservedCopper < fee || state.economy.moneyCopper < fee) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_STATE,
            detail = "Insufficient funds for payout: fee=${fee}, reserved=${state.economy.reservedCopper}, money=${state.economy.moneyCopper}"
        )
    }

    return ValidationResult.Valid
}

/**
 * Validates SellTrophies command.
 */
@Suppress("ReturnCount")
private fun validateSellTrophies(state: GameState, cmd: SellTrophies): ValidationResult {
    if (cmd.amount <= 0) {
        return ValidationResult.Valid
    }
    if (state.economy.trophiesStock < cmd.amount) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_STATE,
            detail = "Insufficient trophies: need ${cmd.amount}, have ${state.economy.trophiesStock}"
        )
    }
    return ValidationResult.Valid
}

/**
 * Validates PayTax command.
 */
@Suppress("ReturnCount")
private fun validatePayTax(state: GameState, cmd: PayTax): ValidationResult {
    if (cmd.amount <= 0) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_ARG,
            detail = "amount=${cmd.amount} must be > 0"
        )
    }

    if (state.economy.moneyCopper < cmd.amount) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_STATE,
            detail = "Insufficient funds: need ${cmd.amount}, have ${state.economy.moneyCopper}"
        )
    }

    return ValidationResult.Valid
}

/**
 * Validates CreateContract command.
 */
@Suppress("ReturnCount")
private fun validateCreateContract(state: GameState, cmd: CreateContract): ValidationResult {
    if (cmd.title.isBlank()) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_ARG,
            detail = "title cannot be blank"
        )
    }

    if (cmd.difficulty < 0 || cmd.difficulty > 100) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_ARG,
            detail = "difficulty=${cmd.difficulty} must be in [0, 100]"
        )
    }

    if (cmd.reward < 0) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_ARG,
            detail = "reward=${cmd.reward} must be >= 0"
        )
    }

    return ValidationResult.Valid
}

/**
 * Validates UpdateContractTerms command.
 */
@Suppress("ReturnCount")
private fun validateUpdateContractTerms(state: GameState, cmd: UpdateContractTerms): ValidationResult {
    // Check if contract exists in inbox or board
    val inboxContract = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.contractId }
    val boardContract = state.contracts.board.firstOrNull { it.id.value.toLong() == cmd.contractId }

    if (inboxContract == null && boardContract == null) {
        return ValidationResult.Rejected(
            reason = RejectReason.NOT_FOUND,
            detail = "contractId=${cmd.contractId} not found in inbox or board"
        )
    }

    // Cannot update locked or completed contracts
    if (boardContract != null && boardContract.status != BoardStatus.OPEN) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_STATE,
            detail = "Cannot update contract with status=${boardContract.status}, only OPEN contracts can be updated"
        )
    }

    // Validate new fee
    if (cmd.newFee != null && cmd.newFee < 0) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_ARG,
            detail = "newFee=${cmd.newFee} must be >= 0"
        )
    }

    // If updating board contract fee, check escrow delta
    if (boardContract != null && cmd.newFee != null) {
        val currentFee = boardContract.fee
        val feeDelta = cmd.newFee - currentFee
        if (feeDelta > 0) {
            val availableCopper = state.economy.moneyCopper - state.economy.reservedCopper
            if (availableCopper < feeDelta) {
                return ValidationResult.Rejected(
                    reason = RejectReason.INVALID_STATE,
                    detail = "Insufficient available funds for fee increase: need ${feeDelta}, available ${availableCopper}"
                )
            }
        }
    }

    return ValidationResult.Valid
}

/**
 * Validates CancelContract command.
 */
@Suppress("ReturnCount")
private fun validateCancelContract(state: GameState, cmd: CancelContract): ValidationResult {
    // Check if contract exists in inbox or board
    val inboxContract = state.contracts.inbox.firstOrNull { it.id.value.toLong() == cmd.contractId }
    val boardContract = state.contracts.board.firstOrNull { it.id.value.toLong() == cmd.contractId }

    if (inboxContract == null && boardContract == null) {
        return ValidationResult.Rejected(
            reason = RejectReason.NOT_FOUND,
            detail = "contractId=${cmd.contractId} not found in inbox or board"
        )
    }

    // Cannot cancel locked or completed contracts
    if (boardContract != null && boardContract.status != BoardStatus.OPEN) {
        return ValidationResult.Rejected(
            reason = RejectReason.INVALID_STATE,
            detail = "Cannot cancel contract with status=${boardContract.status}, only OPEN contracts can be cancelled"
        )
    }

    return ValidationResult.Valid
}
