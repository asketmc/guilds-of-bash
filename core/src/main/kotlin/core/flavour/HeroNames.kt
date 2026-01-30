// FILE: core/src/main/kotlin/core/flavour/HeroNames.kt
package core.flavour

/**
 * ## Role
 * - Provides the deterministic pool of hero names for arriving heroes.
 *
 * ## Contract
 * - POOL is a fixed list of ASCII names (characters 32..126 only).
 * - Names are assigned randomly via RNG during hero creation.
 * - Names are human, medieval / low-fantasy styled (no fantasy races).
 *
 * ## Stability
 * - Stable API: yes; Audience: core reducer only
 */
internal object HeroNames {
    val POOL = listOf(
        // Legacy names (kept for stability / backward compatibility)
        "Smith",
        "Jos",
        "Anna",
        "White",
        "Jack",

        // Male
        "Alaric",
        "Edric",
        "Godfrey",
        "Baldric",
        "Oswin",
        "Cedric",
        "Hugh",
        "Roland",
        "Tristan",
        "Wilfred",

        // Female
        "Aveline",
        "Isolde",
        "Rowena",
        "Matilda",
        "Elowen",
        "Adela",
        "Beatrice",
        "Helena",
        "Margery",
        "Sybil"
    )
}
