// FILE: core/src/main/kotlin/core/Commands.kt
package core

import core.primitives.ActiveContractId
import core.primitives.ContractId
import core.primitives.Rank
import core.primitives.SalvagePolicy

sealed interface Command {
    val cmdId: Long
}

data class PostContract(val inboxId: Long, val fee: Int, override val cmdId: Long): Command
data class CloseReturn(val activeContractId: Long, override val cmdId: Long): Command
data class AdvanceDay(override val cmdId: Long): Command
data class SellTrophies(val amount: Int, override val cmdId: Long): Command
