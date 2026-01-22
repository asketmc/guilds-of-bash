package core.primitives

/**
 * ## Role
 * - Trophy distribution policy in `ContractTerms`. When `acceptProof == true`: `HERO` keeps all stacks hero-owned, `GUILD` transfers all to guild, `SPLIT` distributes via `guildCount = floor(totalCount * splitRatioGuild / 100)`.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class SalvagePolicy {
    /**
     * All trophy stacks remain hero-owned.
     */
    HERO,

    /**
     * All trophy stacks become guild-owned.
     */
    GUILD,

    /**
     * Trophy stacks distributed between guild and hero; Target uses parameterized `splitRatioGuild`, code defaults to 50/50.
     */
    SPLIT  // 50/50 distribution between guild and hero
}
