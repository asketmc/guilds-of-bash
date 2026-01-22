package core.primitives

/**
 * Unique identifier for a board contract. Participates in stable ordering (tie-break by `contractId`) and referential integrity constraints.
 *
 * ## Invariants
 * - Uniqueness: no two board contracts share the same `contractId`.
 *
 * @property value Numeric ID; range: positive integers.
 */
@JvmInline
value class ContractId(val value: Int)

/**
 * Unique identifier for a hero. Participates in stable ordering (heroes sorted by `heroId`) and referential integrity constraints.
 *
 * ## Invariants
 * - Uniqueness: no two heroes share the same `heroId`.
 *
 * @property value Numeric ID; range: positive integers.
 */
@JvmInline
value class HeroId(val value: Int)

/**
 * Unique identifier for an active contract. Participates in stable ordering (tie-break by `activeId`) and event tracing (`ContractTaken.activeId`, `ReturnClosed.activeId`).
 *
 * ## Invariants
 * - Uniqueness: no two active contracts share the same `activeId`.
 *
 * @property value Numeric ID; range: positive integers.
 */
@JvmInline
value class ActiveContractId(val value: Int)
