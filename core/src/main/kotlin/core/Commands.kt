// FILE: core/src/main/kotlin/core/Commands.kt
package core

import core.primitives.ActiveContractId
import core.primitives.ContractId
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.primitives.ProofPolicy

/**
 * Player-issued instruction to mutate game state.
 *
 * ## Role
 * - Root of the Command → `step()` → Events contract.
 * - Every state transition originates from exactly one Command.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 *
 * @property cmdId Unique command identifier for replay and audit (> 0).
 */
sealed interface Command {
    val cmdId: Long
}

/**
 * Posts a draft contract from inbox to the public board.
 *
 * This is the player's commitment point: funds are escrowed and the contract becomes
 * visible for hero pickup.
 *
 * @property inboxId ID of the draft in inbox to post.
 * @property fee Copper amount the guild commits to pay the hero on completion (>= 0).
 * @property salvage Trophy distribution policy chosen by the player.
 * @property cmdId Unique command identifier (> 0).
 */
data class PostContract(
    val inboxId: Long,
    val fee: Int,
    val salvage: SalvagePolicy,
    override val cmdId: Long
): Command

/**
 * Closes a completed return, finalizing rewards and hero status.
 *
 * Required for PARTIAL outcomes; auto-closed returns do not need this command.
 *
 * @property activeContractId ID of the active contract whose return to close.
 * @property cmdId Unique command identifier (> 0).
 */
data class CloseReturn(val activeContractId: Long, override val cmdId: Long): Command

/**
 * Advances the simulation by one day.
 *
 * Triggers the full day pipeline: inbox generation, hero arrivals, auto-resolve,
 * contract pickup, WIP progression, resolution, stability update, tax evaluation,
 * and day-end snapshot.
 *
 * @property cmdId Unique command identifier (> 0).
 */
data class AdvanceDay(override val cmdId: Long): Command

/**
 * Sells trophies from guild inventory for copper currency.
 *
 * Current exchange rate: 1 trophy = 1 copper.
 *
 * @property amount Number of trophies to sell (> 0, capped at inventory).
 * @property cmdId Unique command identifier (> 0).
 */
data class SellTrophies(val amount: Int, override val cmdId: Long): Command

/**
 * Pays outstanding tax debt to the realm.
 *
 * Payment is applied to penalty first, then to principal.
 *
 * @property amount Copper amount to pay (> 0).
 * @property cmdId Unique command identifier (> 0).
 */
data class PayTax(val amount: Int, override val cmdId: Long): Command

/**
 * Changes the guild's proof validation policy.
 *
 * STRICT policy blocks closure on damaged proof or suspected theft.
 * FAST policy allows closure regardless of proof quality.
 *
 * @property policy New proof policy to adopt.
 * @property cmdId Unique command identifier (> 0).
 */
data class SetProofPolicy(
    val policy: ProofPolicy,
    override val cmdId: Long
): Command

/**
 * Creates a new contract draft in the inbox.
 *
 * Used by external tools or adapters to inject custom contracts into the game.
 *
 * @property title Human-readable contract title (non-blank).
 * @property rank Target difficulty/reward tier.
 * @property difficulty Base difficulty scalar (0..100).
 * @property reward Client's contribution towards the fee in copper (>= 0).
 * @property salvage Default trophy distribution policy.
 * @property cmdId Unique command identifier (> 0).
 */
data class CreateContract(
    val title: String,
    val rank: Rank,
    val difficulty: Int,
    val reward: Int,
    val salvage: SalvagePolicy,
    override val cmdId: Long
): Command

/**
 * Updates terms of an existing contract in inbox or on board.
 *
 * Only OPEN board contracts can be updated. Fee increases require sufficient funds.
 *
 * @property contractId ID of the contract to update.
 * @property newFee New fee amount in copper (null = keep current).
 * @property newSalvage New salvage policy (null = keep current).
 * @property cmdId Unique command identifier (> 0).
 */
data class UpdateContractTerms(
    val contractId: Long,
    val newFee: Int?,
    val newSalvage: SalvagePolicy?,
    override val cmdId: Long
): Command

/**
 * Cancels a contract from inbox or board.
 *
 * Inbox cancellation has no economic impact. Board cancellation refunds the client deposit.
 * Only OPEN board contracts can be cancelled.
 *
 * @property contractId ID of the contract to cancel.
 * @property cmdId Unique command identifier (> 0).
 */
data class CancelContract(
    val contractId: Long,
    override val cmdId: Long
): Command
