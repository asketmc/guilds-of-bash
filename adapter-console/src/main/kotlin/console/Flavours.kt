/**
 * Flavour text generation for console adapter.
 * R1-R4 Compliance: normalization, phase ordering, overlays, UnknownEvent suppression.
 */
package console

import core.*
import core.primitives.Outcome
import core.primitives.Quality
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.state.GameState
import core.state.Hero
import core.state.Traits
import java.security.MessageDigest

// ============================================================================
// DETERMINISTIC HASH UTILITIES
// ============================================================================

private fun stableHash32(key: String): Int {
    val digest = MessageDigest.getInstance("SHA-256")
    val bytes = digest.digest(key.toByteArray(Charsets.UTF_8))
    return ((bytes[0].toInt() and 0xFF) shl 24) or
        ((bytes[1].toInt() and 0xFF) shl 16) or
        ((bytes[2].toInt() and 0xFF) shl 8) or
        (bytes[3].toInt() and 0xFF)
}

internal fun pick2(key: String): Int = stableHash32(key) and 1

// ============================================================================
// TEXT NORMALIZATION (R1)
// ============================================================================

private val TERMINAL_PUNCTUATION = setOf('.', '!', '?')
private val CLOSING_CHARS = setOf('"', '\'', ')', ']', '}')

internal fun normalizeLine(line: String): String {
    if (line.isBlank()) return line.trim()
    var result = line
    // Fix punctuation followed by letter without space
    result = result.replace(Regex("""([.!?:;,])([a-zA-Z])""")) { m ->
        "${m.groupValues[1]} ${m.groupValues[2]}"
    }
    // Fix closing quote followed by letter without space (only after punctuation or end of word)
    // This handles cases like: said."The -> said." The
    // But NOT opening quotes like: "hello -> should stay as "hello
    result = result.replace(Regex("""([.!?])(['"])([a-zA-Z])""")) { m ->
        "${m.groupValues[1]}${m.groupValues[2]} ${m.groupValues[3]}"
    }
    // Remove double spaces
    result = result.replace(Regex("""\s{2,}"""), " ").trim()
    result = capitalizeFirstLetter(result)
    return ensureTerminalPunctuation(result)
}

private fun capitalizeFirstLetter(line: String): String {
    if (line.isEmpty()) return line
    // Handle [Day N] prefix
    Regex("""^(\[[^]]*]\s*)(.*)""").find(line)?.let {
        return it.groupValues[1] + capitalizeFirstChar(it.groupValues[2])
    }
    // Handle leading quote
    Regex("""^(["']\s*)(.*)""").find(line)?.let {
        return it.groupValues[1] + capitalizeFirstChar(it.groupValues[2])
    }
    return capitalizeFirstChar(line)
}

private fun capitalizeFirstChar(s: String): String {
    if (s.isEmpty()) return s
    val first = s[0]
    return if (first.isLetter() && first.isLowerCase()) {
        first.uppercaseChar() + s.substring(1)
    } else {
        s
    }
}

private fun ensureTerminalPunctuation(line: String): String {
    if (line.isEmpty()) return line
    val last = line.last()
    if (last in TERMINAL_PUNCTUATION) return line
    if (last in CLOSING_CHARS && line.length > 1 && line[line.length - 2] in TERMINAL_PUNCTUATION) return line
    return "$line."
}

// ============================================================================
// HERO PERSONALITY DERIVATION
// ============================================================================

private fun clampTrait(value: Int): Int = value.coerceIn(0, 100)

private fun traitToVibe(value: Int): TraitVibe {
    val clamped = clampTrait(value)
    return when {
        clamped <= 33 -> TraitVibe.LOW
        clamped <= 66 -> TraitVibe.MID
        else -> TraitVibe.HIGH
    }
}

fun describeHero(traits: Traits): HeroVibe = HeroVibe(
    greedVibe = traitToVibe(traits.greed),
    honestyVibe = traitToVibe(traits.honesty),
    courageVibe = traitToVibe(traits.courage)
)

private fun heroAdjectives(vibe: HeroVibe): String {
    val adj = mutableListOf<String>()
    when (vibe.greedVibe) {
        TraitVibe.HIGH -> adj.add("greedy")
        TraitVibe.LOW -> adj.add("generous")
        TraitVibe.MID -> { }
    }
    if (adj.size < 2) {
        when (vibe.courageVibe) {
            TraitVibe.LOW -> adj.add("cautious")
            TraitVibe.HIGH -> adj.add("reckless")
            TraitVibe.MID -> { }
        }
    }
    if (adj.size < 2) {
        when (vibe.honestyVibe) {
            TraitVibe.LOW -> adj.add("shifty")
            TraitVibe.HIGH -> adj.add("honorable")
            TraitVibe.MID -> { }
        }
    }
    return adj.joinToString(" ")
}

private fun formatHeroRef(hero: Hero?): String {
    if (hero == null) return "an unknown adventurer"
    val vibe = describeHero(hero.traits)
    val adj = heroAdjectives(vibe)
    val klass = hero.klass.name.lowercase().replaceFirstChar { it.uppercase() }
    return if (adj.isNotEmpty()) "the $adj $klass ${hero.name}" else "the $klass ${hero.name}"
}

// ============================================================================
// CONTRACT STORY TAG DERIVATION
// ============================================================================

fun storyForContract(contractId: Long, rank: Rank, difficulty: Int): StoryTag {
    val key = "$contractId|${rank.ordinal}|$difficulty"
    return if (pick2(key) == 0) StoryTag.GOBLINS_NEARBY else StoryTag.MISSING_CARAVAN
}

private fun storyTagHook(tag: StoryTag, v: Int): String = when (tag) {
    StoryTag.GOBLINS_NEARBY -> if (v == 0) "Farmers report goblin tracks near the old mill."
        else "The stench of goblin camps drifts from the eastern woods."
    StoryTag.MISSING_CARAVAN -> if (v == 0) "A merchant caravan vanished on the northern road three days past."
        else "The Duke's wine shipment never arrived; foul play is suspected."
}

private fun storyTagResolution(tag: StoryTag, outcome: Outcome, v: Int): String = when (tag) {
    StoryTag.GOBLINS_NEARBY -> when (outcome) {
        Outcome.SUCCESS -> if (v == 0) "The goblin warren lies empty now, its denizens scattered to the winds."
            else "Green blood stains the forest floor - the goblin threat is ended."
        Outcome.PARTIAL -> if (v == 0) "Some goblins escaped into the deep caves, but their leaders are slain."
            else "The goblins retreated, but they'll be back."
        Outcome.FAIL, Outcome.DEATH -> if (v == 0) "The goblins proved more cunning than expected."
            else "Ambushed in the goblin tunnels - a costly lesson."
    }
    StoryTag.MISSING_CARAVAN -> when (outcome) {
        Outcome.SUCCESS -> if (v == 0) "The caravan's survivors were found hiding in a cave."
            else "Justice served - the bandits hang from the roadside gibbets."
        Outcome.PARTIAL -> if (v == 0) "Half the cargo was already sold in the black markets."
            else "The merchant lives, though his purse is considerably lighter."
        Outcome.FAIL, Outcome.DEATH -> if (v == 0) "The caravan's fate remains a mystery."
            else "The bandits vanished like morning mist."
    }
}

// ============================================================================
// NARRATIVE PHASE ORDERING (R2)
// ============================================================================

internal enum class NarrativePhase(val order: Int) {
    DAY_STARTED(0),
    INBOX_ARRIVALS(1),
    CONTRACT_POSTED(2),
    CONTRACT_TAKEN(3),
    WIP_ADVANCED(4),
    CONTRACT_RESOLVED(5),
    RETURN_CLOSED(6),
    STABILITY_TAX_SHUTDOWN(7),
    DAY_ENDED(8),
    OTHER(99)
}

private fun eventToPhase(event: Event): NarrativePhase = when (event) {
    is DayStarted -> NarrativePhase.DAY_STARTED
    is InboxGenerated, is HeroesArrived -> NarrativePhase.INBOX_ARRIVALS
    is ContractPosted -> NarrativePhase.CONTRACT_POSTED
    is ContractTaken, is HeroDeclined -> NarrativePhase.CONTRACT_TAKEN
    is WipAdvanced -> NarrativePhase.WIP_ADVANCED
    is ContractResolved, is TrophyTheftSuspected -> NarrativePhase.CONTRACT_RESOLVED
    is ReturnClosed -> NarrativePhase.RETURN_CLOSED
    is StabilityUpdated, is TaxDue, is TaxPaid, is TaxMissed, is GuildShutdown, is GuildRankUp -> NarrativePhase.STABILITY_TAX_SHUTDOWN
    is DayEnded -> NarrativePhase.DAY_ENDED
    else -> NarrativePhase.OTHER
}

// ============================================================================
// EVENT RENDERING
// ============================================================================

private fun eventKey(type: String, day: Int, cmdId: Long, seq: Long, heroId: Int? = null, contractId: Int? = null): String {
    val parts = mutableListOf("$type|d=$day|cmd=$cmdId|seq=$seq")
    heroId?.let { parts.add("h=$it") }
    contractId?.let { parts.add("c=$it") }
    return parts.joinToString("|")
}

private class RenderContext(
    val pre: GameState,
    val post: GameState,
    val events: List<Event>
) {
    fun findHero(heroId: Int): Hero? = post.heroes.roster.find { it.id.value == heroId }

    fun findBoardContract(contractId: Int) =
        post.contracts.board.find { it.id.value == contractId }
            ?: pre.contracts.board.find { it.id.value == contractId }

    fun findSalvagePolicyForActive(activeContractId: Int): SalvagePolicy? {
        val ac = pre.contracts.active.find { it.id.value == activeContractId }
            ?: post.contracts.active.find { it.id.value == activeContractId }
        val bcId = ac?.boardContractId?.value ?: return null
        return (pre.contracts.board.find { it.id.value == bcId }
            ?: post.contracts.board.find { it.id.value == bcId })?.salvage
    }

    val resolvedByOutcome by lazy {
        events.filterIsInstance<ContractResolved>().groupingBy { it.outcome }.eachCount()
    }

    val hasTaxMissed by lazy { events.any { it is TaxMissed } }
}

private fun renderEventLine(event: Event, ctx: RenderContext): String? {
    val day = event.day
    return when (event) {
        is DayStarted -> {
            val v = pick2(eventKey("DayStarted", day, event.cmdId, event.seq))
            if (v == 0) "[Day $day] Dawn breaks over the guild hall."
            else "[Day $day] Another morning in the guild."
        }
        is InboxGenerated -> {
            if (event.count == 0) return null
            val v = pick2(eventKey("InboxGenerated", day, event.cmdId, event.seq))
            val plural = if (event.count > 1) "s" else ""
            if (v == 0) "A courier arrives with ${event.count} new contract$plural."
            else "The innkeeper slides ${event.count} rumpled notice$plural across the bar."
        }
        is HeroesArrived -> {
            if (event.count == 0) return null
            val v = pick2(eventKey("HeroesArrived", day, event.cmdId, event.seq))
            val p = event.count > 1
            if (v == 0) "${event.count} adventurer${if (p) "s push" else " pushes"} through the tavern doors."
            else "The guild roster grows by ${event.count}."
        }
        is ContractPosted -> {
            val c = ctx.findBoardContract(event.boardContractId)
            val tag = c?.let { storyForContract(it.id.value.toLong(), it.rank, it.baseDifficulty) }
            val v = pick2(eventKey("ContractPosted", day, event.cmdId, event.seq, contractId = event.boardContractId))
            val hook = tag?.let { storyTagHook(it, v) } ?: "Another local request finds its way to the board."
            if (v == 0) "A ${event.rank}-rank contract is nailed to the board. $hook" else hook
        }
        is ContractTaken -> {
            val heroes = event.heroIds.map { ctx.findHero(it) }
            val hd = when {
                heroes.size == 1 -> formatHeroRef(heroes[0])
                heroes.size == 2 -> "${formatHeroRef(heroes[0])} and ${formatHeroRef(heroes[1])}"
                else -> "${heroes.size} brave souls"
            }
            val v = pick2(eventKey("ContractTaken", day, event.cmdId, event.seq, contractId = event.boardContractId))
            if (v == 0) "$hd steps forward, accepting the contract."
            else "The contract changes hands."
        }
        is ContractResolved -> {
            val c = ctx.findBoardContract(event.activeContractId)
                ?: ctx.pre.contracts.board.find { bc ->
                    ctx.pre.contracts.active.any { ac ->
                        ac.id.value == event.activeContractId && ac.boardContractId.value == bc.id.value
                    }
                }
            val tag = c?.let { storyForContract(it.id.value.toLong(), it.rank, it.baseDifficulty) }
            val v = pick2(eventKey("ContractResolved", day, event.cmdId, event.seq, contractId = event.activeContractId))
            if (tag != null) {
                storyTagResolution(tag, event.outcome, v)
            } else {
                when (event.outcome) {
                    Outcome.SUCCESS -> if (v == 0) "The contract is fulfilled." else "Another job done proper."
                    Outcome.PARTIAL -> if (v == 0) "A messy finish." else "Partial success."
                    Outcome.FAIL, Outcome.DEATH -> if (v == 0) "Failure." else "The heroes return empty-handed."
                }
            }
        }
        is HeroDeclined -> {
            val h = formatHeroRef(ctx.findHero(event.heroId))
            val v = pick2(eventKey("HeroDeclined", day, event.cmdId, event.seq, heroId = event.heroId, contractId = event.boardContractId))
            val r = when (event.reason) {
                "low_profit" -> if (v == 0) "The coin's not worth the risk." else "I've got expensive tastes."
                "too_risky" -> if (v == 0) "I value my neck more than your contract." else "That's suicide, not a job."
                else -> if (v == 0) "Not interested." else "I'll pass."
            }
            "$h scoffs: \"$r\""
        }
        is TrophyTheftSuspected -> {
            val h = formatHeroRef(ctx.findHero(event.heroId))
            val v = pick2(eventKey("TrophyTheftSuspected", day, event.cmdId, event.seq, heroId = event.heroId))
            val d = event.expectedTrophies - event.reportedTrophies
            if (v == 0) "The ledger doesn't add up. $h returned with $d fewer trophies than expected."
            else "$h's trophy count seems light."
        }
        is StabilityUpdated -> {
            val delta = event.newStability - event.oldStability
            val v = pick2(eventKey("StabilityUpdated", day, event.cmdId, event.seq))
            when {
                delta > 5 -> if (v == 0) "The roads grow safer." else "Order returns to the region."
                delta > 0 -> if (v == 0) "Stability improves slightly." else "A little more peace in the realm."
                delta < -5 -> if (v == 0) "Unrest spreads like wildfire." else "Dark times ahead."
                delta < 0 -> if (v == 0) "Troubling reports from the frontier." else "The balance tips toward disorder."
                else -> null
            }
        }
        is TaxDue -> {
            val v = pick2(eventKey("TaxDue", day, event.cmdId, event.seq))
            if (v == 0) "A sealed letter arrives bearing the Crown's seal. Tax of ${event.amountDue} copper due by day ${event.dueDay}."
            else "The tax collector's notice: \"${event.amountDue} copper, due day ${event.dueDay}.\""
        }
        is TaxPaid -> {
            val v = pick2(eventKey("TaxPaid", day, event.cmdId, event.seq))
            if (event.isPartialPayment) {
                if (v == 0) "A partial payment of ${event.amountPaid} copper."
                else "${event.amountPaid} copper handed over."
            } else {
                if (v == 0) "The guild's debts to the Crown are settled."
                else "Tax paid."
            }
        }
        is TaxMissed -> {
            val v = pick2(eventKey("TaxMissed", day, event.cmdId, event.seq))
            if (v == 0) "Tax deadline missed! Penalty: ${event.penaltyAdded} copper. Strike ${event.missedCount}."
            else "The inspectors note another missed payment. Strike ${event.missedCount}."
        }
        is GuildRankUp -> {
            val v = pick2(eventKey("GuildRankUp", day, event.cmdId, event.seq))
            if (v == 0) "The guild's banner rises higher! Rank ${event.oldRank} to ${event.newRank}."
            else "Word spreads of the guild's achievements."
        }
        is GuildShutdown -> {
            val v = pick2(eventKey("GuildShutdown", day, event.cmdId, event.seq))
            if (v == 0) "The guild hall's shutters close for the last time. Reason: ${event.reason}."
            else "\"By order of the Crown, this guild is hereby dissolved.\""
        }
        is DayEnded -> null
        else -> null
    }
}

// ============================================================================
// EXPLANATORY OVERLAYS (R3)
// ============================================================================

private fun generateContractResolvedOverlays(event: ContractResolved, ctx: RenderContext): List<String> {
    val overlays = mutableListOf<String>()
    when (event.quality) {
        Quality.DAMAGED -> overlays.add("The proof is battered; the broker will pay less.")
        Quality.NONE -> overlays.add("No proof, no trophies - only a story.")
        Quality.OK -> { }
    }
    if (event.trophiesCount > 0 && event.outcome != Outcome.FAIL) {
        overlays.add("Trophies recovered: ${event.trophiesCount}.")
    }
    val salvage = ctx.findSalvagePolicyForActive(event.activeContractId)
    if (salvage == SalvagePolicy.HERO && event.trophiesCount > 0) {
        overlays.add("The hero keeps the salvage; the guild gains reputation, not loot.")
    }
    return overlays
}

// ============================================================================
// DAY SUMMARY
// ============================================================================

private fun determineSummaryBucket(ctx: RenderContext): DaySummaryBucket {
    val r = ctx.resolvedByOutcome
    val s = r[Outcome.SUCCESS] ?: 0
    val f = r[Outcome.FAIL] ?: 0
    return when {
        f > 0 || ctx.hasTaxMissed -> DaySummaryBucket.BAD_OMENS
        s > 0 -> DaySummaryBucket.GOOD_DAY
        else -> DaySummaryBucket.QUIET_DAY
    }
}

private fun renderDaySummary(bucket: DaySummaryBucket, day: Int, eventsHash: String): String {
    val v = pick2("summary|day=$day|hash=$eventsHash")
    return when (bucket) {
        DaySummaryBucket.QUIET_DAY -> if (v == 0) "[Day $day ends] A quiet day." else "[Day $day ends] Uneventful."
        DaySummaryBucket.GOOD_DAY -> if (v == 0) "[Day $day ends] A good day's work." else "[Day $day ends] Success breeds success."
        DaySummaryBucket.BAD_OMENS -> if (v == 0) "[Day $day ends] Ill omens." else "[Day $day ends] A dark day for the guild."
    }
}

// ============================================================================
// PUBLIC API
// ============================================================================

private const val MAX_NARRATIVE_LINES = 8

private fun eventPriority(event: Event): Int = when (event) {
    is DayStarted -> 0
    is GuildShutdown -> 1
    is GuildRankUp -> 2
    is TaxMissed -> 3
    is TaxDue -> 4
    is ContractResolved -> 5
    is TrophyTheftSuspected -> 6
    is ContractTaken -> 7
    is HeroDeclined -> 8
    is StabilityUpdated -> 9
    is ContractPosted -> 10
    is HeroesArrived -> 11
    is InboxGenerated -> 12
    is TaxPaid -> 13
    else -> 99
}

fun renderDayNarrative(
    pre: GameState,
    post: GameState,
    events: List<Event>,
    debugMode: Boolean = false
): List<String> {
    if (events.isEmpty()) return emptyList()
    val ctx = RenderContext(pre, post, events)
    val allLines = mutableListOf<String>()

    data class IndexedEvent(val event: Event, val phase: NarrativePhase, val index: Int)
    val byPhase = events.mapIndexed { i, e -> IndexedEvent(e, eventToPhase(e), i) }.groupBy { it.phase }

    for (phase in NarrativePhase.entries.sortedBy { it.order }) {
        val pe = byPhase[phase]?.sortedBy { it.index }?.map { it.event } ?: continue
        if (phase == NarrativePhase.OTHER && !debugMode) continue
        if (phase == NarrativePhase.WIP_ADVANCED || phase == NarrativePhase.RETURN_CLOSED) continue
        for (ev in pe) {
            if (ev is DayEnded) continue
            renderEventLine(ev, ctx)?.let { allLines.add(normalizeLine(it)) }
            if (ev is ContractResolved) {
                generateContractResolvedOverlays(ev, ctx).forEach { allLines.add(normalizeLine(it)) }
            }
        }
    }

    events.filterIsInstance<DayEnded>().firstOrNull()?.let { de ->
        allLines.add(normalizeLine(renderDaySummary(determineSummaryBucket(ctx), de.day, events.hashCode().toString(16))))
    }

    return if (allLines.size <= MAX_NARRATIVE_LINES) allLines else trimToMaxLines(allLines)
}

private fun trimToMaxLines(allLines: List<String>): List<String> {
    if (allLines.size <= MAX_NARRATIVE_LINES) return allLines
    val first = allLines.firstOrNull()
    val last = allLines.lastOrNull()
    val mid = allLines.drop(1).dropLast(1).take(MAX_NARRATIVE_LINES - 2)
    return buildList {
        first?.let { add(it) }
        addAll(mid)
        if (last != null && last != first) add(last)
    }
}

fun renderCommandHook(pre: GameState, post: GameState, events: List<Event>): String? {
    if (events.isEmpty()) return null
    val ctx = RenderContext(pre, post, events)
    return events.filter { it !is CommandRejected && it !is InvariantViolated }
        .minByOrNull { eventPriority(it) }
        ?.let { renderEventLine(it, ctx)?.let { l -> normalizeLine(l) } }
}
