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
        is DayEnded -> serializeDayEnded(event, sb)
        is CommandRejected -> serializeCommandRejected(event, sb)
        is InvariantViolated -> serializeInvariantViolated(event, sb)
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

private fun StringBuilder.appendLongField(name: String, value: Long) {
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
