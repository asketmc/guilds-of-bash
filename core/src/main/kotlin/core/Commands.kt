// FILE: core/src/main/kotlin/core/Commands.kt
package core

import core.primitives.ActiveContractId
import core.primitives.ContractId
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.primitives.ProofPolicy

sealed interface Command {
    val cmdId: Long
}

data class PostContract(
    val inboxId: Long,
    val fee: Int,
    val salvage: SalvagePolicy,  // Player chooses GUILD/HERO/SPLIT
    override val cmdId: Long
): Command
data class CloseReturn(val activeContractId: Long, override val cmdId: Long): Command
data class AdvanceDay(override val cmdId: Long): Command
data class SellTrophies(val amount: Int, override val cmdId: Long): Command

data class PayTax(val amount: Int, override val cmdId: Long): Command
data class SetProofPolicy(
    val policy: ProofPolicy,
    override val cmdId: Long
): Command

// R1: Lifecycle commands
data class CreateContract(
    val title: String,
    val rank: Rank,
    val difficulty: Int,
    val reward: Int,
    val salvage: SalvagePolicy,
    override val cmdId: Long
): Command

data class UpdateContractTerms(
    val contractId: Long,
    val newFee: Int?,
    val newSalvage: SalvagePolicy?,
    override val cmdId: Long
): Command

data class CancelContract(
    val contractId: Long,
    override val cmdId: Long
): Command
