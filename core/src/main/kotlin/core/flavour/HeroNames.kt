// FILE: core/src/main/kotlin/core/flavour/HeroNames.kt
package core.flavour

/**
 * ## Role
 * - Provides the deterministic pool of hero names for arriving heroes.
 *
 * ## Contract
 * - POOL is a fixed list of exactly 5 ASCII names (characters 32..126 only).
 * - Names are assigned randomly via RNG during hero creation.
 *
 * ## Stability
 * - Stable API: yes; Audience: core reducer only
 */
internal object HeroNames {
    val POOL = listOf("Smith", "Jos", "Anna", "White", "Jack")
}
