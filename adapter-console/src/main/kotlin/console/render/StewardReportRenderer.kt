package console.render

import core.ContractPosted
import core.Event
import core.InboxGenerated
import core.hash.hashState
import core.primitives.ActiveStatus
import core.state.GameState

/**
 * Input DTO for steward report. Built from existing data only.
 */
data class StewardReportInput(
    val day: Int,
    val revision: Long,
    val stateHash: String,

    // Treasury
    val coin: Int,
    val reserved: Int,
    val available: Int,
    val trophies: Int,

    // Operations
    val inboxOffers: Int,
    val postedToday: Int,
    val active: Int,
    val returnsNeedingAttention: Int,

    // Guild standing
    val stability: Int,
    val reputation: Int?,
    val rank: Int,
    val completedTotal: Int,

    // Crown obligations
    val taxDueDay: Int,
    val owed: Int,
    val penalties: Int,
    val missed: Int,

    // Concerns (0..N)
    val concerns: List<String>
)

object StewardReportRenderer {

    fun renderStewardReport(input: StewardReportInput, cfg: RenderConfig): String {
        val sections: List<List<String>> = listOf(
            listOf(
                "Day ${input.day} of operations, Revision ${input.revision}",
                "",
                "TREASURY:",
            ) + BoxRenderer.labelValueRows(
                listOf(
                    "Coin" to input.coin.toString(),
                    "Reserved" to input.reserved.toString(),
                    "Available" to input.available.toString(),
                    "Trophies" to input.trophies.toString(),
                ),
                cfg
            ),
            listOf("OPERATIONS:") + BoxRenderer.labelValueRows(
                listOf(
                    "Inbox offers" to input.inboxOffers.toString(),
                    "Posted" to input.postedToday.toString(),
                    "Active" to input.active.toString(),
                    "Returns needing attention" to input.returnsNeedingAttention.toString(),
                ),
                cfg
            ),
            listOf("GUILD STANDING:") + BoxRenderer.labelValueRows(
                listOf(
                    "Stability" to input.stability.toString(),
                    "Reputation" to (input.reputation?.toString() ?: "N/A"),
                    "Rank" to input.rank.toString(),
                    "Completed total" to input.completedTotal.toString(),
                ),
                cfg
            ),
            listOf("CROWN OBLIGATIONS:") + BoxRenderer.labelValueRows(
                listOf(
                    "Tax due day" to input.taxDueDay.toString(),
                    "Owed" to input.owed.toString(),
                    "Penalties" to input.penalties.toString(),
                    "Missed" to input.missed.toString(),
                ),
                cfg
            ),
            buildList {
                add("CONCERNS:")
                if (input.concerns.isEmpty()) add("(none)")
                else input.concerns.forEach { add("? $it") }
            }
        )

        return "hash=${input.stateHash}\n" +
            BoxRenderer.boxWithSections("STEWARD REPORT", sections, cfg)
    }

    fun from(newState: GameState, events: List<Event>): StewardReportInput {
        // Events are included only for stable counts; no RNG reads.
        val postedToday = events.count { it is ContractPosted }
        events.filterIsInstance<InboxGenerated>().sumOf { it.count } // intentionally unused but validated stable

        val activeCount = newState.contracts.active.count {
            it.status == ActiveStatus.WIP || it.status == ActiveStatus.RETURN_READY
        }
        val returnsNeedingClose = newState.contracts.returns.count { it.requiresPlayerClose }

        val coin = newState.economy.moneyCopper
        val reserved = newState.economy.reservedCopper
        val available = coin - reserved

        val concerns = computeConcerns(
            stability = newState.region.stability,
            inboxOffers = newState.contracts.inbox.size,
            available = available,
            returnsNeedingClose = returnsNeedingClose,
        )

        return StewardReportInput(
            day = newState.meta.dayIndex,
            revision = newState.meta.revision,
            stateHash = hashState(newState),
            coin = coin,
            reserved = reserved,
            available = available,
            trophies = newState.economy.trophiesStock,
            inboxOffers = newState.contracts.inbox.size,
            postedToday = postedToday,
            active = activeCount,
            returnsNeedingAttention = returnsNeedingClose,
            stability = newState.region.stability,
            reputation = newState.guild.reputation,
            rank = newState.guild.guildRank,
            completedTotal = newState.guild.completedContractsTotal,
            taxDueDay = newState.meta.taxDueDay,
            owed = newState.meta.taxAmountDue,
            penalties = newState.meta.taxPenalty,
            missed = newState.meta.taxMissedCount,
            concerns = concerns
        )
    }

    private fun computeConcerns(
        stability: Int,
        inboxOffers: Int,
        available: Int,
        returnsNeedingClose: Int,
    ): List<String> {
        val out = mutableListOf<String>()

        // Simple fixed rules; do not read RNG.
        if (stability <= 0) out.add("Stability has collapsed. Expect chaos in the streets.")

        val inboxOverloadThreshold = 10
        if (inboxOffers > inboxOverloadThreshold) {
            out.add("Offer board overloaded ($inboxOffers). Consider posting contracts.")
        }

        val minFeeThreshold = 50
        if (available < minFeeThreshold) out.add("Low funds ($available). You may struggle to post fees.")

        if (returnsNeedingClose > 0) out.add("Pending returns ($returnsNeedingClose) require closing.")
        return out
    }
}
