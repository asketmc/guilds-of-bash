package core.primitives

/**
 * ## Role
 * - Hero archetype participating in RNG draw and schema; no class-specific effects defined in current scope.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class HeroClass {
    /**
     * Warrior archetype.
     */
    WARRIOR,

    /**
     * Mage archetype.
     */
    MAGE,

    /**
     * Healer archetype.
     */
    HEAL
}
