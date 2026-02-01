package core.serde

import core.*

/**
 * Canonical JSON serialization for domain [Event] lists.
 *
 * The output is intended to be stable across runs given identical in-memory [Event] instances:
 * - Compact JSON (no insignificant whitespace).
 * - Explicit, event-type-specific field order.
 * - Type discriminator is always the first JSON key.
 * - Common fields are always present and emitted in fixed order: `type`, `day`, `revision`, `cmdId`, `seq`.
 *
 * ## Contract
 * - Produces a single JSON array containing one JSON object per input event, in the same order as `events`.
 * - Each event is serialized according to the concrete subtype branch in [serializeEvent].
 * - Strings are escaped by [escapeJson] for the subset of escapes implemented there.
 *
 * ## Preconditions
 * - All numeric fields must already satisfy their domain constraints (this serializer does not validate).
 * - Each event subtype listed in [serializeEvent] must have a corresponding `serializeXxx` implementation.
 *
 * ## Postconditions
 * - Returned string is valid JSON for the emitted subset of types and fields.
 * - No trailing commas are emitted.
 *
 * ## Invariants
 * - Field order is stable by construction (append-only in a fixed sequence).
 * - Event order is preserved (`events[0]` becomes JSON element 0, etc.).
 *
 * ## Determinism
 * - Deterministic for a fixed `events` sequence and fixed per-event field values.
 * - Does not read wall-clock time or global mutable state.
 *
 * ## Complexity
 * - Time: O(total_output_chars)
 * - Memory: O(total_output_chars)
 *
 * @param events Events to serialize; order is preserved.
 * @return Compact canonical JSON array string.
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

/**
 * Serializes a single [Event] into `sb` as one JSON object (without surrounding commas/array brackets).
 *
 * ## Contract
 * - Appends exactly one JSON object to `sb`.
 * - Dispatch is performed by Kotlin `when` on the runtime subtype of [Event].
 *
 * ## Preconditions
 * - `event` runtime type must be covered by one of the `when` branches; otherwise compilation fails when
 *   [Event] is sealed and extended, or a missing branch will be required when new subtypes are added.
 *
 * ## Postconditions
 * - `sb` is appended with a JSON object that begins with `{` and ends with `}`.
 *
 * ## Invariants
 * - The first field appended by each serializer must be produced by [StringBuilder.appendCommonFields].
 *
 * ## Determinism
 * - Deterministic given deterministic `event` data.
 *
 * ## Complexity
 * - Time: O(event_output_chars)
 * - Memory: O(event_output_chars) incremental append
 *
 * @param event Concrete event instance to serialize.
 * @param sb Destination buffer to append to.
 */
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
        is ContractAutoResolved -> serializeContractAutoResolved(event, sb)
        is HeroDied -> serializeHeroDied(event, sb)
        is ReturnClosureBlocked -> serializeReturnClosureBlocked(event, sb)
    }
}

/**
 * Appends the JSON object prefix and the common fields shared by all events.
 *
 * Output shape prefix (no closing `}`):
 * `{"type":"<type>","day":<n>,"revision":<n>,"cmdId":<n>,"seq":<n>`
 *
 * ## Contract
 * - Must be called exactly once at the beginning of each event-specific serializer.
 * - Does not close the JSON object; the caller must append additional fields (optional) and close with `}`.
 *
 * ## Preconditions
 * - `type` must match the discriminator string expected by the corresponding deserializer (if any).
 *
 * ## Postconditions
 * - `sb` contains a `{` and five fields in fixed order: `type`, `day`, `revision`, `cmdId`, `seq`.
 *
 * ## Determinism
 * - Deterministic given `type` and `event` field values.
 *
 * ## Complexity
 * - Time: O(1) relative to event size (constant number of appends)
 * - Memory: O(1) incremental append
 *
 * @param type Type discriminator string written into the JSON `type` field.
 * @param event Event providing `day`, `revision`, `cmdId`, `seq`.
 */
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

/**
 * Appends a string field to an existing JSON object being built in this [StringBuilder].
 *
 * The emitted fragment has the form: `,"<name>":"<escaped>"`
 *
 * ## Contract
 * - Prepends a comma before the field (caller must already be inside an object).
 * - Escapes the value using [escapeJson].
 *
 * ## Preconditions
 * - Caller has already started a JSON object and has not closed it.
 *
 * ## Postconditions
 * - `sb` is appended with a JSON string field.
 *
 * ## Complexity
 * - Time: O(value.length)
 * - Memory: O(1) incremental append
 *
 * @param name JSON field name (unescaped; emitted as-is inside quotes).
 * @param value Raw string value; will be escaped by [escapeJson].
 */
private fun StringBuilder.appendStringField(name: String, value: String) {
    append(",\"")
    append(name)
    append("\":\"")
    append(escapeJson(value))
    append("\"")
}

/**
 * Appends an integer field to an existing JSON object being built in this [StringBuilder].
 *
 * The emitted fragment has the form: `,"<name>":<value>`
 *
 * ## Contract
 * - Prepends a comma before the field.
 * - Writes the integer value as a JSON number (no quotes).
 *
 * ## Preconditions
 * - Caller has already started a JSON object and has not closed it.
 *
 * ## Postconditions
 * - `sb` is appended with a JSON numeric field.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 *
 * @param name JSON field name.
 * @param value Integer value.
 */
private fun StringBuilder.appendIntField(name: String, value: Int) {
    append(",\"")
    append(name)
    append("\":")
    append(value)
}

/**
 * Appends an `IntArray` field to an existing JSON object being built in this [StringBuilder].
 *
 * The emitted fragment has the form: `,"<name>":[v0,v1,...]`
 *
 * ## Contract
 * - Prepends a comma before the field.
 * - Emits values in the array's iteration order with comma separation and no whitespace.
 *
 * ## Preconditions
 * - Caller has already started a JSON object and has not closed it.
 *
 * ## Postconditions
 * - `sb` is appended with a JSON array of numbers (possibly empty).
 *
 * ## Complexity
 * - Time: O(array.size)
 * - Memory: O(1) incremental append
 *
 * @param name JSON field name.
 * @param array Integer array to emit.
 */
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

/**
 * Appends a nullable integer field to an existing JSON object being built in this [StringBuilder].
 *
 * The emitted fragment has the form: `,"<name>":null` or `,"<name>":<value>`
 *
 * ## Contract
 * - Prepends a comma before the field.
 * - Emits JSON `null` when `value == null`.
 *
 * ## Preconditions
 * - Caller has already started a JSON object and has not closed it.
 *
 * ## Postconditions
 * - `sb` is appended with a JSON numeric-or-null field.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 *
 * @param name JSON field name.
 * @param value Nullable integer; `null` is emitted as JSON `null`.
 */
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

/**
 * Appends a nullable string field to an existing JSON object being built in this [StringBuilder].
 *
 * The emitted fragment has the form: `,"<name>":null` or `,"<name>":"<escaped>"`
 *
 * ## Contract
 * - Prepends a comma before the field.
 * - Emits JSON `null` when `value == null`.
 * - Escapes non-null values via [escapeJson].
 *
 * ## Preconditions
 * - Caller has already started a JSON object and has not closed it.
 *
 * ## Postconditions
 * - `sb` is appended with a JSON string-or-null field.
 *
 * ## Complexity
 * - Time: O(value.length) when non-null; otherwise O(1)
 * - Memory: O(1) incremental append
 *
 * @param name JSON field name.
 * @param value Nullable string; non-null values are escaped.
 */
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

/**
 * Escapes a string for embedding into a JSON string literal.
 *
 * Implemented escapes:
 * - `\` -> `\\`
 * - `"` -> `\"`
 * - newline -> `\n`
 * - carriage return -> `\r`
 * - tab -> `\t`
 *
 * ## Contract
 * - Returns a string that can be safely inserted between JSON quotes for the above characters.
 *
 * ## Preconditions
 * - Input must not contain other control characters that require JSON escaping if strict JSON compliance
 *   for all code points is required; this function does not implement full JSON escaping.
 *
 * ## Postconditions
 * - Returned string has no unescaped occurrences of the handled characters.
 *
 * ## Determinism
 * - Pure function over `str`.
 *
 * ## Complexity
 * - Time: O(str.length)
 * - Memory: O(str.length) (due to successive replacements)
 *
 * @param str Raw string to escape.
 * @return Escaped string suitable for JSON string literal content.
 */
private fun escapeJson(str: String): String {
    return str
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t")
}

// Event-specific serializers

/**
 * Serializes [DayStarted] into a JSON object with only common fields.
 *
 * ## Contract
 * - Emits: `{"type":"DayStarted","day":...,"revision":...,"cmdId":...,"seq":...}`
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeDayStarted(event: DayStarted, sb: StringBuilder) {
    sb.appendCommonFields("DayStarted", event)
    sb.append('}')
}

/**
 * Serializes [InboxGenerated].
 *
 * Emitted additional fields (in order):
 * - `count` (Int)
 * - `contractIds` (IntArray)
 *
 * ## Complexity
 * - Time: O(contractIds.size)
 * - Memory: O(1)
 */
private fun serializeInboxGenerated(event: InboxGenerated, sb: StringBuilder) {
    sb.appendCommonFields("InboxGenerated", event)
    sb.appendIntField("count", event.count)
    sb.appendIntArray("contractIds", event.contractIds)
    sb.append('}')
}

/**
 * Serializes [HeroesArrived].
 *
 * Emitted additional fields (in order):
 * - `count` (Int)
 * - `heroIds` (IntArray)
 *
 * ## Complexity
 * - Time: O(heroIds.size)
 * - Memory: O(1)
 */
private fun serializeHeroesArrived(event: HeroesArrived, sb: StringBuilder) {
    sb.appendCommonFields("HeroesArrived", event)
    sb.appendIntField("count", event.count)
    sb.appendIntArray("heroIds", event.heroIds)
    sb.append('}')
}

/**
 * Serializes [ContractPosted].
 *
 * Emitted additional fields (in order):
 * - `boardContractId` (Int)
 * - `fromInboxId` (Int)
 * - `rank` (String; `event.rank.name`)
 * - `fee` (Int)
 * - `salvage` (String; `event.salvage.name`)
 * - `clientDeposit` (Int; client's contribution towards the fee)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeContractPosted(event: ContractPosted, sb: StringBuilder) {
    sb.appendCommonFields("ContractPosted", event)
    sb.appendIntField("boardContractId", event.boardContractId)
    sb.appendIntField("fromInboxId", event.fromInboxId)
    sb.appendStringField("rank", event.rank.name)
    sb.appendIntField("fee", event.fee)
    sb.appendStringField("salvage", event.salvage.name)
    sb.appendIntField("clientDeposit", event.clientDeposit)
    sb.append('}')
}

/**
 * Serializes [ContractTaken].
 *
 * Emitted additional fields (in order):
 * - `activeContractId` (Int)
 * - `boardContractId` (Int)
 * - `heroIds` (IntArray)
 * - `daysRemaining` (Int)
 *
 * ## Complexity
 * - Time: O(heroIds.size)
 * - Memory: O(1)
 */
private fun serializeContractTaken(event: ContractTaken, sb: StringBuilder) {
    sb.appendCommonFields("ContractTaken", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendIntField("boardContractId", event.boardContractId)
    sb.appendIntArray("heroIds", event.heroIds)
    sb.appendIntField("daysRemaining", event.daysRemaining)
    sb.append('}')
}

/**
 * Serializes [WipAdvanced].
 *
 * Emitted additional fields (in order):
 * - `activeContractId` (Int)
 * - `daysRemaining` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeWipAdvanced(event: WipAdvanced, sb: StringBuilder) {
    sb.appendCommonFields("WipAdvanced", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendIntField("daysRemaining", event.daysRemaining)
    sb.append('}')
}

/**
 * Serializes [ContractResolved].
 *
 * Emitted additional fields (in order):
 * - `activeContractId` (Int)
 * - `outcome` (String; `event.outcome.name`)
 * - `trophiesCount` (Int)
 * - `quality` (String; `event.quality.name`)
 * - `reasonTags` (IntArray)
 *
 * ## Complexity
 * - Time: O(reasonTags.size)
 * - Memory: O(1)
 */
private fun serializeContractResolved(event: ContractResolved, sb: StringBuilder) {
    sb.appendCommonFields("ContractResolved", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendStringField("outcome", event.outcome.name)
    sb.appendIntField("trophiesCount", event.trophiesCount)
    sb.appendStringField("quality", event.quality.name)
    sb.appendIntArray("reasonTags", event.reasonTags)
    sb.append('}')
}

/**
 * Serializes [ReturnClosed].
 *
 * Emitted additional fields (in order):
 * - `activeContractId` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeReturnClosed(event: ReturnClosed, sb: StringBuilder) {
    sb.appendCommonFields("ReturnClosed", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.append('}')
}

/**
 * Serializes [TrophySold].
 *
 * Emitted additional fields (in order):
 * - `amount` (Int)
 * - `moneyGained` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeTrophySold(event: TrophySold, sb: StringBuilder) {
    sb.appendCommonFields("TrophySold", event)
    sb.appendIntField("amount", event.amount)
    sb.appendIntField("moneyGained", event.moneyGained)
    sb.append('}')
}

/**
 * Serializes [StabilityUpdated].
 *
 * Emitted additional fields (in order):
 * - `oldStability` (Int)
 * - `newStability` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeStabilityUpdated(event: StabilityUpdated, sb: StringBuilder) {
    sb.appendCommonFields("StabilityUpdated", event)
    sb.appendIntField("oldStability", event.oldStability)
    sb.appendIntField("newStability", event.newStability)
    sb.append('}')
}

/**
 * Serializes [DayEnded] including its nested `snapshot` object.
 *
 * Emitted additional fields:
 * - `snapshot` (object) with a fixed set of numeric fields (as appended below).
 *
 * ## Contract
 * - `snapshot` is emitted as an inline object; field order inside `snapshot` is the append order in this method.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
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

/**
 * Serializes [CommandRejected].
 *
 * Emitted additional fields (in order):
 * - `cmdType` (String)
 * - `reason` (String; `event.reason.name`)
 * - `detail` (String; escaped)
 *
 * ## Complexity
 * - Time: O(detail.length)
 * - Memory: O(1)
 */
private fun serializeCommandRejected(event: CommandRejected, sb: StringBuilder) {
    sb.appendCommonFields("CommandRejected", event)
    sb.appendStringField("cmdType", event.cmdType)
    sb.appendStringField("reason", event.reason.name)
    sb.appendStringField("detail", event.detail)
    sb.append('}')
}

/**
 * Serializes [InvariantViolated].
 *
 * Emitted additional fields (in order):
 * - `invariantId` (String; `event.invariantId.name`)
 * - `details` (String; escaped)
 *
 * ## Complexity
 * - Time: O(details.length)
 * - Memory: O(1)
 */
private fun serializeInvariantViolated(event: InvariantViolated, sb: StringBuilder) {
    sb.appendCommonFields("InvariantViolated", event)
    sb.appendStringField("invariantId", event.invariantId.name)
    sb.appendStringField("details", event.details)
    sb.append('}')
}

/**
 * Serializes [HeroDeclined].
 *
 * Emitted additional fields (in order):
 * - `heroId` (Int)
 * - `boardContractId` (Int)
 * - `reason` (String; escaped)
 *
 * ## Complexity
 * - Time: O(reason.length)
 * - Memory: O(1)
 */
private fun serializeHeroDeclined(event: HeroDeclined, sb: StringBuilder) {
    sb.appendCommonFields("HeroDeclined", event)
    sb.appendIntField("heroId", event.heroId)
    sb.appendIntField("boardContractId", event.boardContractId)
    sb.appendStringField("reason", event.reason)
    sb.append('}')
}

/**
 * Serializes [TrophyTheftSuspected].
 *
 * Emitted additional fields (in order):
 * - `activeContractId` (Int)
 * - `heroId` (Int)
 * - `expectedTrophies` (Int)
 * - `reportedTrophies` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeTrophyTheftSuspected(event: TrophyTheftSuspected, sb: StringBuilder) {
    sb.appendCommonFields("TrophyTheftSuspected", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendIntField("heroId", event.heroId)
    sb.appendIntField("expectedTrophies", event.expectedTrophies)
    sb.appendIntField("reportedTrophies", event.reportedTrophies)
    sb.append('}')
}

/**
 * Serializes [TaxDue].
 *
 * Emitted additional fields (in order):
 * - `amountDue` (Int)
 * - `dueDay` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeTaxDue(event: TaxDue, sb: StringBuilder) {
    sb.appendCommonFields("TaxDue", event)
    sb.appendIntField("amountDue", event.amountDue)
    sb.appendIntField("dueDay", event.dueDay)
    sb.append('}')
}

/**
 * Serializes [TaxPaid].
 *
 * Emitted additional fields (in order):
 * - `amountPaid` (Int)
 * - `amountDue` (Int)
 * - `isPartialPayment` (Boolean)
 *
 * ## Contract
 * - Boolean is emitted as JSON `true`/`false` without quotes.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeTaxPaid(event: TaxPaid, sb: StringBuilder) {
    sb.appendCommonFields("TaxPaid", event)
    sb.appendIntField("amountPaid", event.amountPaid)
    sb.appendIntField("amountDue", event.amountDue)
    sb.append(",\"isPartialPayment\":")
    sb.append(if (event.isPartialPayment) "true" else "false")
    sb.append('}')
}

/**
 * Serializes [TaxMissed].
 *
 * Emitted additional fields (in order):
 * - `amountDue` (Int)
 * - `penaltyAdded` (Int)
 * - `missedCount` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeTaxMissed(event: TaxMissed, sb: StringBuilder) {
    sb.appendCommonFields("TaxMissed", event)
    sb.appendIntField("amountDue", event.amountDue)
    sb.appendIntField("penaltyAdded", event.penaltyAdded)
    sb.appendIntField("missedCount", event.missedCount)
    sb.append('}')
}

/**
 * Serializes [GuildShutdown].
 *
 * Emitted additional fields (in order):
 * - `reason` (String; escaped)
 *
 * ## Complexity
 * - Time: O(reason.length)
 * - Memory: O(1)
 */
private fun serializeGuildShutdown(event: GuildShutdown, sb: StringBuilder) {
    sb.appendCommonFields("GuildShutdown", event)
    sb.appendStringField("reason", event.reason)
    sb.append('}')
}

/**
 * Serializes [GuildRankUp].
 *
 * Emitted additional fields (in order):
 * - `oldRank` (Int)
 * - `newRank` (Int)
 * - `completedContracts` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeGuildRankUp(event: GuildRankUp, sb: StringBuilder) {
    sb.appendCommonFields("GuildRankUp", event)
    sb.appendIntField("oldRank", event.oldRank)
    sb.appendIntField("newRank", event.newRank)
    sb.appendIntField("completedContracts", event.completedContracts)
    sb.append('}')
}

/**
 * Serializes [ProofPolicyChanged].
 *
 * Emitted additional fields (in order):
 * - `oldPolicy` (Int)
 * - `newPolicy` (Int)
 *
 * ## Note
 * This serializer emits `oldPolicy` and `newPolicy` as integers; the mapping of those integers to a
 * domain enum/type is defined by the event model, not by this serializer.
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeProofPolicyChanged(event: ProofPolicyChanged, sb: StringBuilder) {
    sb.appendCommonFields("ProofPolicyChanged", event)
    sb.appendIntField("oldPolicy", event.oldPolicy)
    sb.appendIntField("newPolicy", event.newPolicy)
    sb.append('}')
}

/**
 * Serializes [ContractDraftCreated].
 *
 * Emitted additional fields (in order):
 * - `draftId` (Int)
 * - `title` (String; escaped)
 * - `rank` (String; `event.rank.name`)
 * - `difficulty` (Int)
 * - `reward` (Int)
 * - `salvage` (String; `event.salvage.name`)
 *
 * ## Complexity
 * - Time: O(title.length)
 * - Memory: O(1)
 */
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

/**
 * Serializes [ContractTermsUpdated].
 *
 * Emitted additional fields (in order):
 * - `contractId` (Int)
 * - `location` (String; escaped)
 * - `oldFee` (Int?; JSON number or null)
 * - `newFee` (Int?; JSON number or null)
 * - `oldSalvage` (String?; JSON string or null; uses `name` when non-null)
 * - `newSalvage` (String?; JSON string or null; uses `name` when non-null)
 *
 * ## Complexity
 * - Time: O(location.length + (oldSalvage?.name?.length ?: 0) + (newSalvage?.name?.length ?: 0))
 * - Memory: O(1)
 */
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

/**
 * Serializes [ContractCancelled].
 *
 * Emitted additional fields (in order):
 * - `contractId` (Int)
 * - `location` (String; escaped)
 * - `refundedCopper` (Int)
 *
 * ## Complexity
 * - Time: O(location.length)
 * - Memory: O(1)
 */
private fun serializeContractCancelled(event: ContractCancelled, sb: StringBuilder) {
    sb.appendCommonFields("ContractCancelled", event)
    sb.appendIntField("contractId", event.contractId)
    sb.appendStringField("location", event.location)
    sb.appendIntField("refundedCopper", event.refundedCopper)
    sb.append('}')
}

/**
 * Serializes [ContractAutoResolved].
 *
 * Emitted additional fields (in order):
 * - `draftId` (Int)
 * - `bucket` (String; `event.bucket.name`)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeContractAutoResolved(event: ContractAutoResolved, sb: StringBuilder) {
    sb.appendCommonFields("ContractAutoResolved", event)
    sb.appendIntField("draftId", event.draftId)
    sb.appendStringField("bucket", event.bucket.name)
    sb.append('}')
}

/**
 * Serializes [HeroDied].
 *
 * Emitted additional fields (in order):
 * - `heroId` (Int)
 * - `activeContractId` (Int)
 * - `boardContractId` (Int)
 *
 * ## Complexity
 * - Time: O(1)
 * - Memory: O(1)
 */
private fun serializeHeroDied(event: HeroDied, sb: StringBuilder) {
    sb.appendCommonFields("HeroDied", event)
    sb.appendIntField("heroId", event.heroId)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendIntField("boardContractId", event.boardContractId)
    sb.append('}')
}

/**
 * Serializes [ReturnClosureBlocked].
 *
 * Emitted additional fields (in order):
 * - `activeContractId` (Int)
 * - `policy` (String; `event.policy.name`)
 * - `reason` (String; escaped)
 *
 * ## Complexity
 * - Time: O(reason.length)
 * - Memory: O(1)
 */
private fun serializeReturnClosureBlocked(event: ReturnClosureBlocked, sb: StringBuilder) {
    sb.appendCommonFields("ReturnClosureBlocked", event)
    sb.appendIntField("activeContractId", event.activeContractId)
    sb.appendStringField("policy", event.policy.name)
    sb.appendStringField("reason", event.reason)
    sb.append('}')
}
