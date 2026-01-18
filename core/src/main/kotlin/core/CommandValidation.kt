// FILE: core/src/main/kotlin/core/CommandValidation.kt
package core

import core.primitives.ActiveStatus
import core.state.GameState

enum class RejectReason {
    NOT_FOUND,
    INVALID_ARG,
    INVALID_STATE
}

data class CommandRejection(
    val reason: RejectReason,
    val detail: String
)

fun canApply(state: GameState, cmd: Command): CommandRejection? =
    when (cmd) {
        is PostContract -> validatePostContract(state, cmd)
        is CloseReturn -> validateCloseReturn(state, cmd)
        is AdvanceDay -> null
    }

private fun validatePostContract(state: GameState, cmd: PostContract): CommandRejection? {
    val inboxExists = state.contracts.inbox.any { it.id == cmd.inboxId }
    if (!inboxExists) {
        return CommandRejection(
            reason = RejectReason.NOT_FOUND,
            detail = "inboxId=${cmd.inboxId} not found"
        )
    }

    if (cmd.fee < 0) {
        return CommandRejection(
            reason = RejectReason.INVALID_ARG,
            detail = "fee=${cmd.fee} must be >= 0 (inboxId=${cmd.inboxId})"
        )
    }

    return null
}

private fun validateCloseReturn(state: GameState, cmd: CloseReturn): CommandRejection? {
    val active = state.contracts.active.firstOrNull { it.id == cmd.activeContractId }
        ?: return CommandRejection(
            reason = RejectReason.NOT_FOUND,
            detail = "activeContractId=${cmd.activeContractId} not found"
        )

    if (active.status != ActiveStatus.RETURN_READY) {
        return CommandRejection(
            reason = RejectReason.INVALID_STATE,
            detail = "activeContractId=${cmd.activeContractId} status=${active.status} expected=${ActiveStatus.RETURN_READY}"
        )
    }

    val returnRequiresClose = state.contracts.returns.any {
        it.activeContractId == cmd.activeContractId && it.requiresPlayerClose
    }
    if (!returnRequiresClose) {
        return CommandRejection(
            reason = RejectReason.NOT_FOUND,
            detail = "return for activeContractId=${cmd.activeContractId} not found or does not require close"
        )
    }

    return null
}
