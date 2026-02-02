/**
 * Compatibility bridge for test infrastructure and legacy API expectations.
 *
 * ## Role
 * - Provides extension functions that adapt current core types to older test patterns.
 * - Enables tests written against Map-based board access to work with List-based storage.
 *
 * ## Stability
 * - Internal API: yes; Audience: tests only
 */
package core

import core.primitives.*
import core.state.BoardContract

/**
 * Adapts board contract list to Map-like access keyed by [ContractId].
 *
 * Legacy tests expect `state.contracts.board.contracts[id]` syntax.
 */
val List<BoardContract>.contracts: Map<ContractId, BoardContract>
    get() = this.associateBy { it.id }

/** Converts [ContractId] to Long for legacy test comparisons. */
fun ContractId.toLong(): Long = this.value.toLong()

/** Converts [ActiveContractId] to Long for legacy test comparisons. */
fun ActiveContractId.toLong(): Long = this.value.toLong()

/** Converts [HeroId] to Long for legacy test comparisons. */
fun HeroId.toLong(): Long = this.value.toLong()

/**
 * Type alias enabling tests to cast rejection events to `Rejected`.
 *
 * Legacy tests use `event as Rejected` instead of `event as CommandRejected`.
 */
typealias Rejected = CommandRejected
