/**
 * Story tags for contract narrative generation.
 *
 * These tags are derived deterministically from contract data and used
 * to generate contextual flavor text for contract events.
 */
package console

/**
 * Mini-story tags for contracts (PoC: only 2 variants).
 *
 * Each tag represents a narrative theme that can be applied to
 * contract-related events for added flavor.
 */
enum class StoryTag {
    /**
     * Goblins have been spotted near the contract location.
     * Implies danger from small but cunning adversaries.
     */
    GOBLINS_NEARBY,

    /**
     * A merchant caravan has gone missing.
     * Implies investigation/rescue themes.
     */
    MISSING_CARAVAN
}

/**
 * Hero personality vibe buckets derived from numeric traits.
 *
 * Each trait (greed, honesty, courage) maps to one of three buckets:
 * LOW (0..33), MID (34..66), HIGH (67..100).
 */
enum class TraitVibe {
    LOW,
    MID,
    HIGH
}

/**
 * Aggregated hero personality description derived from traits.
 *
 * @property greedVibe Vibe bucket for greed trait.
 * @property honestyVibe Vibe bucket for honesty trait.
 * @property courageVibe Vibe bucket for courage trait.
 */
data class HeroVibe(
    val greedVibe: TraitVibe,
    val honestyVibe: TraitVibe,
    val courageVibe: TraitVibe
)

/**
 * Day summary bucket for end-of-day narrative.
 */
enum class DaySummaryBucket {
    QUIET_DAY,
    GOOD_DAY,
    BAD_OMENS
}
