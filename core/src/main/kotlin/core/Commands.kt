// FILE: core/src/main/kotlin/core/Commands.kt
package core

import core.primitives.ActiveContractId
import core.primitives.ContractId
import core.primitives.Rank
import core.primitives.SalvagePolicy

sealed interface Command {
    val cmdId: Long
}

data class PostContract(
    override val cmdId: Long,
    val inboxId: ContractId,
    val rank: Rank,
    val fee: Int,
    val salvage: SalvagePolicy
) : Command

data class CloseReturn(
    override val cmdId: Long,
    val activeContractId: ActiveContractId
) : Command

data class AdvanceDay(
    override val cmdId: Long
) : Command
