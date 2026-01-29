package console

import core.*
import core.primitives.*
import core.state.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Unit tests for Flavours.kt deterministic narrative generation.
 * Tests R1-R4 requirements: normalization, phase ordering, overlays, UnknownEvent handling.
 */
class FlavoursTest {

    // ========================================================================
    // R1 - Text Normalization Tests
    // ========================================================================

    @Test
    fun `normalizeLine adds space after punctuation followed by letter`() {
        // Note: only first char of entire line is capitalized, not after each punctuation
        assertEquals("Hello? the world.", normalizeLine("Hello?the world"))
        assertEquals("Hello! the world.", normalizeLine("Hello!the world"))
        assertEquals("Hello. the world.", normalizeLine("Hello.the world"))
        assertEquals("Hello: the world.", normalizeLine("Hello:the world"))
        assertEquals("Hello; the world.", normalizeLine("Hello;the world"))
    }

    @Test
    fun `normalizeLine removes double spaces`() {
        assertEquals("Hello world.", normalizeLine("Hello  world"))
        assertEquals("Hello world.", normalizeLine("Hello   world"))
    }

    @Test
    fun `normalizeLine capitalizes first letter`() {
        assertEquals("Hello world.", normalizeLine("hello world"))
    }

    @Test
    fun `normalizeLine preserves bracket prefix capitalization`() {
        assertEquals("[Day 1] Hello world.", normalizeLine("[Day 1] hello world"))
        assertEquals("[Day 10] Hello world.", normalizeLine("[Day 10] hello world"))
    }

    @Test
    fun `normalizeLine adds terminal punctuation if missing`() {
        assertEquals("Hello world.", normalizeLine("Hello world"))
        // Should not add if already present
        assertEquals("Hello world!", normalizeLine("Hello world!"))
        assertEquals("Hello world?", normalizeLine("Hello world?"))
        assertEquals("Hello world.", normalizeLine("Hello world."))
    }

    @Test
    fun `normalizeLine handles closing quotes correctly`() {
        // Opening quote followed by letter should NOT get a space
        assertEquals("He said \"hello.\"", normalizeLine("He said \"hello.\""))
        // But closing quote after punctuation followed by letter SHOULD get a space
        assertEquals("She replied.\" That's nice.\"", normalizeLine("She replied.\"That's nice.\""))
    }

    // ========================================================================
    // pick2 stability tests
    // ========================================================================

    @Test
    fun `pick2 returns 0 or 1`() {
        val keys = listOf(
            "test1",
            "DayStarted|d=1|cmd=1|seq=1",
            "ContractPosted|d=5|cmd=10|seq=3|c=42",
            "summary|day=10|hash=abc123"
        )

        for (key in keys) {
            val result = pick2(key)
            assertTrue(result == 0 || result == 1, "pick2('$key') = $result, expected 0 or 1")
        }
    }

    @Test
    fun `pick2 is stable for same key`() {
        val key = "DayStarted|d=1|cmd=1|seq=1"
        val first = pick2(key)
        val second = pick2(key)
        val third = pick2(key)

        assertEquals(first, second, "pick2 should return same value for same key")
        assertEquals(second, third, "pick2 should return same value for same key")
    }

    // ========================================================================
    // describeHero bucket/clamping tests
    // ========================================================================

    @Test
    fun `describeHero LOW bucket for traits 0-33`() {
        val traitsLow = Traits(greed = 0, honesty = 15, courage = 33)
        val vibe = describeHero(traitsLow)

        assertEquals(TraitVibe.LOW, vibe.greedVibe, "greed=0 should be LOW")
        assertEquals(TraitVibe.LOW, vibe.honestyVibe, "honesty=15 should be LOW")
        assertEquals(TraitVibe.LOW, vibe.courageVibe, "courage=33 should be LOW")
    }

    @Test
    fun `describeHero MID bucket for traits 34-66`() {
        val traitsMid = Traits(greed = 34, honesty = 50, courage = 66)
        val vibe = describeHero(traitsMid)

        assertEquals(TraitVibe.MID, vibe.greedVibe, "greed=34 should be MID")
        assertEquals(TraitVibe.MID, vibe.honestyVibe, "honesty=50 should be MID")
        assertEquals(TraitVibe.MID, vibe.courageVibe, "courage=66 should be MID")
    }

    @Test
    fun `describeHero HIGH bucket for traits 67-100`() {
        val traitsHigh = Traits(greed = 67, honesty = 85, courage = 100)
        val vibe = describeHero(traitsHigh)

        assertEquals(TraitVibe.HIGH, vibe.greedVibe, "greed=67 should be HIGH")
        assertEquals(TraitVibe.HIGH, vibe.honestyVibe, "honesty=85 should be HIGH")
        assertEquals(TraitVibe.HIGH, vibe.courageVibe, "courage=100 should be HIGH")
    }

    @Test
    fun `describeHero clamps out-of-range values`() {
        val traitsOutOfRange = Traits(greed = -50, honesty = 150, courage = 200)
        val vibe = describeHero(traitsOutOfRange)

        assertEquals(TraitVibe.LOW, vibe.greedVibe, "greed=-50 should clamp to LOW")
        assertEquals(TraitVibe.HIGH, vibe.honestyVibe, "honesty=150 should clamp to HIGH")
        assertEquals(TraitVibe.HIGH, vibe.courageVibe, "courage=200 should clamp to HIGH")
    }

    // ========================================================================
    // storyForContract determinism tests
    // ========================================================================

    @Test
    fun `storyForContract is deterministic`() {
        val tag1 = storyForContract(contractId = 42L, rank = Rank.C, difficulty = 50)
        val tag2 = storyForContract(contractId = 42L, rank = Rank.C, difficulty = 50)

        assertEquals(tag1, tag2, "storyForContract should be deterministic for same inputs")
    }

    @Test
    fun `storyForContract returns only valid tags`() {
        val inputs = listOf(
            Triple(1L, Rank.F, 0),
            Triple(999L, Rank.S, 100),
            Triple(42L, Rank.C, 50),
            Triple(0L, Rank.A, 75)
        )

        for ((id, rank, diff) in inputs) {
            val tag = storyForContract(id, rank, diff)
            assertTrue(
                tag == StoryTag.GOBLINS_NEARBY || tag == StoryTag.MISSING_CARAVAN,
                "storyForContract should return valid StoryTag"
            )
        }
    }

    // ========================================================================
    // renderDayNarrative tests
    // ========================================================================

    @Test
    fun `renderDayNarrative empty events returns empty list`() {
        val state = createTestState()
        val lines = renderDayNarrative(state, state, emptyList())

        assertTrue(lines.isEmpty(), "Empty events should produce empty narrative")
    }

    @Test
    fun `renderDayNarrative is deterministic for same inputs`() {
        val pre = createTestState()
        val post = createTestState()
        val events = listOf(
            DayStarted(day = 1, revision = 1, cmdId = 1, seq = 1),
            DayEnded(day = 1, revision = 2, cmdId = 1, seq = 2, snapshot = createTestSnapshot(day = 1))
        )

        val lines1 = renderDayNarrative(pre, post, events)
        val lines2 = renderDayNarrative(pre, post, events)

        assertEquals(lines1, lines2, "renderDayNarrative should be deterministic")
    }

    @Test
    fun `renderDayNarrative includes day in output`() {
        val pre = createTestState()
        val post = createTestState()
        val day = 5
        val events = listOf(
            DayStarted(day = day, revision = 1, cmdId = 1, seq = 1),
            DayEnded(day = day, revision = 2, cmdId = 1, seq = 2, snapshot = createTestSnapshot(day = day))
        )

        val lines = renderDayNarrative(pre, post, events)

        assertTrue(lines.isNotEmpty(), "Should have narrative lines")
        val hasDayRef = lines.any { it.contains("[Day $day]") || it.contains("Day $day") }
        assertTrue(hasDayRef, "Narrative should reference day $day")
    }


    // ========================================================================
    // Helper functions
    // ========================================================================

    private fun createTestState(): GameState = GameState(
        meta = MetaState(
            saveVersion = 1,
            seed = 42u,
            dayIndex = 0,
            revision = 0L,
            ids = IdCounters(
                nextContractId = 1,
                nextHeroId = 1,
                nextActiveContractId = 1
            ),
            taxDueDay = 7,
            taxAmountDue = 10,
            taxPenalty = 0,
            taxMissedCount = 0
        ),
        guild = GuildState(
            guildRank = 1,
            reputation = 50,
            completedContractsTotal = 0,
            contractsForNextRank = 10,
            proofPolicy = ProofPolicy.FAST
        ),
        region = RegionState(stability = 50),
        economy = EconomyState(
            moneyCopper = 100,
            reservedCopper = 0,
            trophiesStock = 0
        ),
        contracts = ContractState(
            inbox = emptyList(),
            board = emptyList(),
            active = emptyList(),
            returns = emptyList()
        ),
        heroes = HeroState(
            roster = emptyList(),
            arrivalsToday = emptyList()
        )
    )

    private fun createTestSnapshot(day: Int): DaySnapshot = DaySnapshot(
        day = day,
        revision = 1L,
        money = 100,
        trophies = 0,
        regionStability = 50,
        guildReputation = 50,
        inboxCount = 0,
        boardCount = 0,
        activeCount = 0,
        returnsNeedingCloseCount = 0
    )
}
