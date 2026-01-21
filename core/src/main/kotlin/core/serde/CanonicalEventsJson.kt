package core.serde

import core.*

/**
 * Canonical JSON serialization for events.
 * - Compact (no whitespace)
 * - Explicit field order per event type
 * - Type discriminator first
 * - Common fields in fixed order: type, day, revision, cmdId, seq
 */

fun serializeEvents(events: List<Event>): String {
    val sb = StringBuilder()
    sb.append('[')
    events.forEachIndexed { index, event ->
        if (index > 0) sb.append(',')
        serializeEvent(event, sb)
    }
    sb.append(']')
    return sb.toString()
}

private fun serializeEvent(event: Event, sb: StringBuilder) {
    when (event) {
        is DayStarted -> serializeDayStarted(event, sb)
        is InboxGenerated -> serializeInboxGenerated(event, sb)
        is HeroesArrived -> serializeHeroesArrived(event, sb)
        is ContractPosted -> serializeContractPosted(event, sb)
        is ContractTaken -> serializeContractTaken(event, sb)
        is WipAdvanced -> serializeWipAdvanced(event, sb)
        is ContractResolved -> serializeContractResolved(event, sb)
        is ReturnClosed -> serializeReturnClosed(event, sb)
        is TrophySold -> serializeTrophySold(event, sb)
        is StabilityUpdated -> serializeStabilityUpdated(event, sb)
        is DayEnded -> serializeDayEnded(event, sb)
        is CommandRejected -> serializeCommandRejected(event, sb)
        is InvariantViolated -> serializeInvariantViolated(event, sb)
        is HeroDeclined -> serializeHeroDeclined(event, sb)
        is TrophyTheftSuspected -> serializeTrophyTheftSuspected(event, sb)
        is TaxDue -> serializeTaxDue(event, sb)
        is TaxPaid -> serializeTaxPaid(event, sb)
        is TaxMissed -> serializeTaxMissed(event, sb)
        is GuildShutdown -> serializeGuildShutdown(event, sb)
        is GuildRankUp -> serializeGuildRankUp(event, sb)
        is ProofPolicyChanged -> serializeProofPolicyChanged(event, sb)
        is ContractDraftCreated -> serializeContractDraftCreated(event, sb)
        is ContractTermsUpdated -> serializeContractTermsUpdated(event, sb)
        is ContractCancelled -> serializeContractCancelled(event, sb)
    }
}

// Helper functions for common patterns
private fun StringBuilder.appendCommonFields(type: String, event: Event) {
    append("{\"type\":\"")
    append(type)
    append("\",\"day\":")
    append(event.day)
    append(",\"revision\":")
    append(event.revision)
    append(",\"cmdId\":")
    append(event.cmdId)
    append(",\"seq\":")
    append(event.seq)
}

private fun StringBuilder.appendStringField(name: String, value: String) {
    append(",\"")
    append(name)
    append("\":\"")
    append(escapeJson(value))
    append("\"")
}

private fun StringBuilder.appendIntField(name: String, value: Int) {
    append(",\"")
    append(name)
    append("\":")
    append(value)
}


private fun StringBuilder.appendIntArray(name: String, array: IntArray) {
    append(",\"")
    append(name)
    append("\":[")
    array.forEachIndexed { index, value ->
        if (index > 0) append(',')
        append(value)
    }
    append("]")
}

private fun StringBuilder.appendNullableIntField(name: String, value: Int?) {
    append(",\"")
    append(name)
    append("\":")
    if (value == null) {
        append("null")
    } else {
        append(value)
    }
}

private fun StringBuilder.appendNullableStringField(name: String, value: String?) {
    append(",\"")
    append(name)
    append("\":")
    if (value == null) {
        append("null")
    } else {
        append("\"")
        append(escapeJson(value))
        append("\"")
    }
}

private fun escapeJson(str: String): String {
    return str
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

// Event-specific serializers

private fun serializeDayStarted(event: DayStarted, sb: StringBuilder) {
    sb.appendCommonFields("DayStarted", event)
    sb.append('}')
}

private fun serializeInboxGenerated(event: InboxGenerated, sb: StringBuilder) {
    sb.appendCommonFields("InboxGenerated", event)
    sb.appendIntField("count", event.count)
    sb.appendIntArray("contractIds", event.contractIds)
    sb.append('}')
}

private fun serializeHeroesArrived(event: HeroesArrived, sb: StringBuilder) {
    sb.appendCommonFields("HeroesArrived", event)
    sb.appendIntField("count", event.count)
    sb.appendIntArray("heroIds", event.heroIds)
    sb.append('}')
}

private fun serializeContractPosted(event: ContractPosted, sb: StringBuilder) {
    sb.appendCommonFields("ContractPosted", event)
    sb.appendIntField("boardContractId", event.boardContractId)
    sb.appendIntField("fromInboxId", event.fromInboxId)
    sb.appendStringField("rank", event.rank.name)
    sb.appendIntField("fee", event.fee)
    sb.appendStringField("salvage", event.salvage.name)
    sb.append('}')
}

private fun serializeContractTaken(event: ContractTaken, sb: StringBuilder) {
    sb.appendCommonFields("ContractTaken", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendIntField("boardContractId", event.boardContractId)
    sb.appendIntArray("heroIds", event.heroIds)
    sb.appendIntField("daysRemaining", event.daysRemaining)
    sb.append('}')
}

private fun serializeWipAdvanced(event: WipAdvanced, sb: StringBuilder) {
    sb.appendCommonFields("WipAdvanced", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendIntField("daysRemaining", event.daysRemaining)
    sb.append('}')
}

private fun serializeContractResolved(event: ContractResolved, sb: StringBuilder) {
    sb.appendCommonFields("ContractResolved", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendStringField("outcome", event.outcome.name)
    sb.appendIntField("trophiesCount", event.trophiesCount)
    sb.appendStringField("quality", event.quality.name)
    sb.appendIntArray("reasonTags", event.reasonTags)
    sb.append('}')
}

private fun serializeReturnClosed(event: ReturnClosed, sb: StringBuilder) {
    sb.appendCommonFields("ReturnClosed", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.append('}')
}

private fun serializeTrophySold(event: TrophySold, sb: StringBuilder) {
    sb.appendCommonFields("TrophySold", event)
    sb.appendIntField("amount", event.amount)
    sb.appendIntField("moneyGained", event.moneyGained)
    sb.append('}')
}

private fun serializeStabilityUpdated(event: StabilityUpdated, sb: StringBuilder) {
    sb.appendCommonFields("StabilityUpdated", event)
    sb.appendIntField("oldStability", event.oldStability)
    sb.appendIntField("newStability", event.newStability)
    sb.append('}')
}

private fun serializeDayEnded(event: DayEnded, sb: StringBuilder) {
    sb.appendCommonFields("DayEnded", event)
    sb.append(",\"snapshot\":{")
    
    val snap = event.snapshot
    sb.append("\"day\":")
    sb.append(snap.day)
    sb.append(",\"revision\":")
    sb.append(snap.revision)
    sb.append(",\"money\":")
    sb.append(snap.money)
    sb.append(",\"trophies\":")
    sb.append(snap.trophies)
    sb.append(",\"regionStability\":")
    sb.append(snap.regionStability)
    sb.append(",\"guildReputation\":")
    sb.append(snap.guildReputation)
    sb.append(",\"inboxCount\":")
    sb.append(snap.inboxCount)
    sb.append(",\"boardCount\":")
    sb.append(snap.boardCount)
    sb.append(",\"activeCount\":")
    sb.append(snap.activeCount)
    sb.append(",\"returnsNeedingCloseCount\":")
    sb.append(snap.returnsNeedingCloseCount)
    
    sb.append("}}")
}

private fun serializeCommandRejected(event: CommandRejected, sb: StringBuilder) {
    sb.appendCommonFields("CommandRejected", event)
    sb.appendStringField("cmdType", event.cmdType)
    sb.appendStringField("reason", event.reason.name)
    sb.appendStringField("detail", event.detail)
    sb.append('}')
}

private fun serializeInvariantViolated(event: InvariantViolated, sb: StringBuilder) {
    sb.appendCommonFields("InvariantViolated", event)
    sb.appendStringField("invariantId", event.invariantId.name)
    sb.appendStringField("details", event.details)
    sb.append('}')
}

private fun serializeHeroDeclined(event: HeroDeclined, sb: StringBuilder) {
    sb.appendCommonFields("HeroDeclined", event)
    sb.appendIntField("heroId", event.heroId)
    sb.appendIntField("boardContractId", event.boardContractId)
    sb.appendStringField("reason", event.reason)
    sb.append('}')
}

private fun serializeTrophyTheftSuspected(event: TrophyTheftSuspected, sb: StringBuilder) {
    sb.appendCommonFields("TrophyTheftSuspected", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendIntField("heroId", event.heroId)
    sb.appendIntField("expectedTrophies", event.expectedTrophies)
    sb.appendIntField("reportedTrophies", event.reportedTrophies)
    sb.append('}')
}

private fun serializeTaxDue(event: TaxDue, sb: StringBuilder) {
    sb.appendCommonFields("TaxDue", event)
    sb.appendIntField("amountDue", event.amountDue)
    sb.appendIntField("dueDay", event.dueDay)
    sb.append('}')
}

private fun serializeTaxPaid(event: TaxPaid, sb: StringBuilder) {
    sb.appendCommonFields("TaxPaid", event)
    sb.appendIntField("amountPaid", event.amountPaid)
    sb.appendIntField("amountDue", event.amountDue)
    sb.append(",\"isPartialPayment\":")
    sb.append(if (event.isPartialPayment) "true" else "false")
    sb.append('}')
}

private fun serializeTaxMissed(event: TaxMissed, sb: StringBuilder) {
    sb.appendCommonFields("TaxMissed", event)
    sb.appendIntField("amountDue", event.amountDue)
    sb.appendIntField("penaltyAdded", event.penaltyAdded)
    sb.appendIntField("missedCount", event.missedCount)
    sb.append('}')
}

private fun serializeGuildShutdown(event: GuildShutdown, sb: StringBuilder) {
    sb.appendCommonFields("GuildShutdown", event)
    sb.appendStringField("reason", event.reason)
    sb.append('}')
}

private fun serializeGuildRankUp(event: GuildRankUp, sb: StringBuilder) {
    sb.appendCommonFields("GuildRankUp", event)
    sb.appendIntField("oldRank", event.oldRank)
    sb.appendIntField("newRank", event.newRank)
    sb.appendIntField("completedContracts", event.completedContracts)
    sb.append('}')
}

private fun serializeProofPolicyChanged(event: ProofPolicyChanged, sb: StringBuilder) {
    sb.appendCommonFields("ProofPolicyChanged", event)
    sb.appendIntField("oldPolicy", event.oldPolicy)
    sb.appendIntField("newPolicy", event.newPolicy)
    sb.append('}')
}

private fun serializeContractDraftCreated(event: ContractDraftCreated, sb: StringBuilder) {
    sb.appendCommonFields("ContractDraftCreated", event)
    sb.appendIntField("draftId", event.draftId)
    sb.appendStringField("title", event.title)
    sb.appendStringField("rank", event.rank.name)
    sb.appendIntField("difficulty", event.difficulty)
    sb.appendIntField("reward", event.reward)
    sb.appendStringField("salvage", event.salvage.name)
    sb.append('}')
}

private fun serializeContractTermsUpdated(event: ContractTermsUpdated, sb: StringBuilder) {
    sb.appendCommonFields("ContractTermsUpdated", event)
    sb.appendIntField("contractId", event.contractId)
    sb.appendStringField("location", event.location)
    sb.appendNullableIntField("oldFee", event.oldFee)
    sb.appendNullableIntField("newFee", event.newFee)
    sb.appendNullableStringField("oldSalvage", event.oldSalvage?.name)
    sb.appendNullableStringField("newSalvage", event.newSalvage?.name)
    sb.append('}')
}

private fun serializeContractCancelled(event: ContractCancelled, sb: StringBuilder) {
    sb.appendCommonFields("ContractCancelled", event)
    sb.appendIntField("contractId", event.contractId)
    sb.appendStringField("location", event.location)
    sb.appendIntField("refundedCopper", event.refundedCopper)
    sb.append('}')
}

