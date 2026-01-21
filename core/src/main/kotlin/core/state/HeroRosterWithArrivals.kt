// FILE: core/src/main/kotlin/core/state/HeroRosterWithArrivals.kt
package core.state

import core.primitives.*

/**
 * Hero-related state owned by the core reducer.
 *
 * ## Contract
 * Maintains the full roster plus the day-scoped list of heroes that arrived on the current day.
 *
 * ## Invariants
 * - Hero ids in [roster] are expected to be unique.
 * - [arrivalsToday] are expected to reference heroes present in [roster] (or to be added alongside roster updates).
 * - [arrivalsToday] is expected to be cleared when advancing to a new day before emitting day events.
 *
 * ## Determinism
 * Pure data container. Deterministic arrivals/roster growth require deterministic generation and id counters.
 *
 * @property roster Current full roster of heroes.
 * @property arrivalsToday Hero ids that arrived during the current day-index.
 */
data class HeroState(
    val roster: List<Hero>,
    val arrivalsToday: List<HeroId>
)

/**
 * Hero entity used by the reducer for contract pickup and resolution.
 *
 * ## Contract
 * Represents a single hero with immutable identity ([id]) and mutable gameplay attributes (rank/class/traits/status).
 *
 * ## Invariants
 * - [id] is stable and unique within a roster.
 * - [historyCompleted] is a completed-missions counter (expected >= 0).
 * - Trait values are expected to be bounded (see [Traits]).
 *
 * ## Determinism
 * Pure data container. Deterministic evolution requires deterministic reducer logic and RNG stream.
 *
 * @property id Stable hero identifier ([HeroId]).
 * @property name Human-readable hero name.
 * @property rank Current rank tier ([Rank]).
 * @property klass Current hero class ([HeroClass]).
 * @property traits Personality/behavior traits influencing decisions.
 * @property status Current availability/mission status ([HeroStatus]).
 * @property historyCompleted Completed mission count (>= 0).
 */
data class Hero(
    val id: HeroId,
    val name: String,
    val rank: Rank,
    val klass: HeroClass,
    val traits: Traits,
    val status: HeroStatus,
    val historyCompleted: Int
)

/**
 * Personality/behavior traits used in multiple decision rules.
 *
 * ## Contract
 * Provides numeric trait values used as inputs for attractiveness/risk and theft/behavior heuristics.
 *
 * ## Invariants
 * Current PoC logic treats these as percentage-like scores:
 * - expected range: 0..100 (inclusive)
 *
 * ## Determinism
 * Pure data container.
 *
 * @property greed Greed score (expected 0..100). Higher => stronger preference for personal gain (e.g., salvage value, theft temptation).
 * @property honesty Honesty score (expected 0..100). Higher => reduced theft temptation / more compliant behavior.
 * @property courage Courage score (expected 0..100). Higher => higher risk tolerance / lower danger penalty.
 */
data class Traits(
    val greed: Int,
    val honesty: Int,
    val courage: Int
)
