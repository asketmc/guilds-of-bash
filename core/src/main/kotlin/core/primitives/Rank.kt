package core.primitives

/**
 * ## Role
 * - Difficulty and power tier used in `BoardContract.rank`, `Hero.rank`, and calculations (`heroPowerFromRank`, `difficultyFromContractRank`). Participates in inbox listing output.
 *
 * ## Stability
 * - Stable API: yes; Audience: adapters/tests/internal
 */
enum class Rank {
    /**
     * Lowest rank.
     */
    F,

    /**
     * Rank E.
     */
    E,

    /**
     * Rank D.
     */
    D,

    /**
     * Rank C.
     */
    C,

    /**
     * Rank B.
     */
    B,

    /**
     * Rank A.
     */
    A,

    /**
     * Highest rank.
     */
    S
}
