package core

import core.primitives.*
import core.state.BoardContract

// Compatibility helpers used by tests to bridge older test expectations to current core API.

// Allow calling `.contracts` on the board list (tests expect a Map-like view keyed by ContractId).
val List<BoardContract>.contracts: Map<ContractId, BoardContract>
    get() = this.associateBy { it.id }

// Convenience conversions: tests often call `.toLong()` on id wrapper types.
fun ContractId.toLong(): Long = this.value.toLong()
fun ActiveContractId.toLong(): Long = this.value.toLong()
fun HeroId.toLong(): Long = this.value.toLong()

// Type alias so tests can cast rejection events to `Rejected` as they expect.
typealias Rejected = CommandRejected
