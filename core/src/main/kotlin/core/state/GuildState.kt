// FILE: core/src/main/kotlin/core/state/GuildState.kt
package core.state

data class GuildState(
    val guildRank: Int,
    val reputation: Int,

    // Rank progression (Phase 2)
    val completedContractsTotal: Int,
    val contractsForNextRank: Int,

    // Proof validation policy (Phase 3)
    val proofPolicy: core.primitives.ProofPolicy = core.primitives.ProofPolicy.FAST
)
