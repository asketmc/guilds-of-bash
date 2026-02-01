package console

import core.*
import core.primitives.*
import core.state.*
import console.render.BoxRenderer
import console.render.RenderConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertNotNull

/**
 * Golden output tests for Features 1-5.
 * All tests verify deterministic output for given inputs.
 */
class GoldenOutputTest {

    // ========================================================================
    // Feature 1: Weekly Gazette Summary Tests
    // ========================================================================

    @Test
    fun `gazette buffer starts empty`() {
        val buffer = GazetteBuffer()
        assertFalse(buffer.hasFullWeek(), "New buffer should not have full week")
        assertEquals(0, buffer.size())
    }

    @Test
    fun `gazette buffer accumulates snapshots`() {
        var buffer = GazetteBuffer()
        repeat(5) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 1)))
        }
        assertEquals(5, buffer.size())
        assertFalse(buffer.hasFullWeek())
    }

    @Test
    fun `gazette buffer reaches full week at 7 days`() {
        var buffer = GazetteBuffer()
        repeat(7) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 1)))
        }
        assertTrue(buffer.hasFullWeek(), "Buffer should have full week after 7 days")
        assertEquals(7, buffer.size())
    }

    @Test
    fun `gazette buffer maintains max 7 entries`() {
        var buffer = GazetteBuffer()
        repeat(10) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 1)))
        }
        assertEquals(7, buffer.size())
        assertEquals(4, buffer.oldest()?.day) // Days 4-10 kept
        assertEquals(10, buffer.newest()?.day)
    }

    @Test
    fun `gazette not rendered before 7 days`() {
        var buffer = GazetteBuffer()
        repeat(6) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 1)))
        }
        val current = createGazetteSnapshot(GazetteSnapshotInput(day = 6))
        val result = GazetteRenderer.render(day = 6, buffer, current)
        assertNull(result, "Gazette should not render before 7 days")
    }

    @Test
    fun `gazette renders on day 7 with full buffer`() {
        var buffer = GazetteBuffer()
        repeat(7) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 1)))
        }
        val current = createGazetteSnapshot(GazetteSnapshotInput(day = 7))
        val result = GazetteRenderer.render(day = 7, buffer, current)
        assertNotNull(result, "Gazette should render on day 7")
        assertTrue(result.contains("GUILD GAZETTE"))
    }

    @Test
    fun `gazette renders on day 14 (second week)`() {
        var buffer = GazetteBuffer()
        repeat(7) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 8))) // Days 8-14
        }
        val current = createGazetteSnapshot(GazetteSnapshotInput(day = 14))
        val result = GazetteRenderer.render(day = 14, buffer, current)
        assertNotNull(result)
        assertTrue(result.contains("Week 2"))
    }

    @Test
    fun `gazette not rendered on non-weekly boundary`() {
        var buffer = GazetteBuffer()
        repeat(7) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 1)))
        }
        val current = createGazetteSnapshot(GazetteSnapshotInput(day = 8))
        buffer = buffer.add(current)
        val result = GazetteRenderer.render(day = 8, buffer, current)
        assertNull(result, "Gazette should not render on day 8")
    }

    @Test
    fun `gazette headlines deterministic for same input`() {
        var buffer = GazetteBuffer()
        repeat(7) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 1, stability = 50 - i * 2)))
        }
        val current = createGazetteSnapshot(GazetteSnapshotInput(day = 7, stability = 38))

        val headlines1 = GazetteHeadlines.generate(buffer, current)
        val headlines2 = GazetteHeadlines.generate(buffer, current)

        assertEquals(headlines1, headlines2, "Headlines should be deterministic")
    }

    @Test
    fun `gazette unrest headline on stability drop`() {
        var buffer = GazetteBuffer()
        buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = 1, stability = 60)))
        repeat(6) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 2, stability = 60 - i * 2)))
        }
        val current = createGazetteSnapshot(GazetteSnapshotInput(day = 7, stability = 50)) // Delta = -10

        val headlines = GazetteHeadlines.generate(buffer, current)
        assertTrue(headlines.any { it.contains("CHAOS") || it.contains("UNREST") || it.contains("TENSIONS") })
    }

    @Test
    fun `gazette prosperity headline on gold increase`() {
        var buffer = GazetteBuffer()
        buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = 1, gold = 100)))
        repeat(6) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 2, gold = 100 + i * 20)))
        }
        val current = createGazetteSnapshot(GazetteSnapshotInput(day = 7, gold = 250)) // Delta = +150

        val headlines = GazetteHeadlines.generate(buffer, current)
        assertTrue(headlines.any { it.contains("TRADE") || it.contains("PROSPERITY") || it.contains("GOLDEN") })
    }

    @Test
    fun `gazette bureaucracy headline on returns`() {
        var buffer = GazetteBuffer()
        repeat(6) { i ->
            buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = i + 1, returnsCount = 0)))
        }
        buffer = buffer.add(createGazetteSnapshot(GazetteSnapshotInput(day = 7, returnsCount = 2)))
        val current = createGazetteSnapshot(GazetteSnapshotInput(day = 7, returnsCount = 2))

        val headlines = GazetteHeadlines.generate(buffer, current)
        assertTrue(headlines.any { it.contains("PAPERWORK") })
    }

    // ========================================================================
    // Feature 2: BoxRenderer Rendering Tests
    // ========================================================================

    @Test
    fun `BoxRenderer renders consistent borders`() {
        val cfg = RenderConfig(renderWidth = 80, useUnicodeBorders = true)
        val out = BoxRenderer.box("TEST", listOf("Content"), cfg)
        val lines = out.split("\n").filter { it.isNotEmpty() }

        assertTrue(lines.first().startsWith("┌"))
        assertTrue(lines.first().endsWith("┐"))
        assertTrue(lines.last().startsWith("└"))
        assertTrue(lines.last().endsWith("┘"))
    }

    @Test
    fun `BoxRenderer renders title centered`() {
        val cfg = RenderConfig(renderWidth = 80, useUnicodeBorders = true)
        val out = BoxRenderer.box("TITLE", listOf("Content"), cfg)
        val lines = out.split("\n").filter { it.isNotEmpty() }

        val titleLine = lines[1]
        assertTrue(titleLine.contains("TITLE"))
        assertTrue(titleLine.startsWith("│"))
        assertTrue(titleLine.endsWith("│"))
    }

    @Test
    fun `BoxRenderer respects configured fixed width`() {
        val cfg = RenderConfig(renderWidth = 80, useUnicodeBorders = true)
        val out = BoxRenderer.box("TEST", listOf("Short", "Medium length content", "A very long line " + "x".repeat(100)), cfg)
        val lines = out.split("\n").filter { it.isNotEmpty() }

        for (line in lines) {
            assertEquals(80, line.length, "Line should be exactly 80 chars")
        }
    }

    @Test
    fun `BoxRenderer wraps long content`() {
        val cfg = RenderConfig(renderWidth = 80, useUnicodeBorders = true)
        val longContent = "A".repeat(120)
        val out = BoxRenderer.box("TEST", listOf(longContent), cfg)
        val lines = out.split("\n").filter { it.isNotEmpty() }

        // content lines start with border and aren't title or divider
        val contentLines = lines.filter { it.startsWith("│") && !it.contains("TEST") && !it.contains("─") }
        assertTrue(contentLines.size >= 2, "Long content should be wrapped")
    }

    @Test
    fun `BoxRenderer boxWithSections includes dividers`() {
        val cfg = RenderConfig(renderWidth = 80, useUnicodeBorders = true)
        val sections = listOf(
            listOf("Section 1 line 1", "Section 1 line 2"),
            listOf("Section 2 line 1")
        )
        val out = BoxRenderer.boxWithSections("TITLE", sections, cfg)
        val lines = out.split("\n").filter { it.isNotEmpty() }

        val dividers = lines.count { it.startsWith("├") && it.endsWith("┤") }
        assertEquals(2, dividers, "Should have title divider plus section divider")
    }

    @Test
    fun `BoxRenderer rendering is deterministic`() {
        val cfg = RenderConfig(renderWidth = 80, useUnicodeBorders = true)
        val rows = listOf("Line 1", "Line 2", "Line 3")

        val render1 = BoxRenderer.box("TITLE", rows, cfg)
        val render2 = BoxRenderer.box("TITLE", rows, cfg)

        assertEquals(render1, render2, "BoxRenderer rendering should be deterministic")
    }

    // ========================================================================
    // Feature 3: Diegetic Help and Status Tests
    // ========================================================================

    @Test
    fun `diegetic help contains all commands`() {
        val text = DiegeticHelp.render()

        // Verify all commands are present
        assertTrue(text.contains("help"), "Should contain help command")
        assertTrue(text.contains("status"), "Should contain status command")
        assertTrue(text.contains("quit"), "Should contain quit command")
        assertTrue(text.contains("list inbox"), "Should contain list inbox command")
        assertTrue(text.contains("list board"), "Should contain list board command")
        assertTrue(text.contains("day"), "Should contain day command")
        assertTrue(text.contains("post"), "Should contain post command")
        assertTrue(text.contains("sell"), "Should contain sell command")
        assertTrue(text.contains("tax pay"), "Should contain tax pay command")
    }

    @Test
    fun `diegetic help has scribe framing`() {
        val text = DiegeticHelp.render()

        assertTrue(text.contains("SCRIBE'S NOTE"), "Should have scribe title")
        assertTrue(text.contains("Scribe"), "Should mention Scribe")
    }

    @Test
    fun `diegetic help is deterministic`() {
        val render1 = DiegeticHelp.render()
        val render2 = DiegeticHelp.render()

        assertEquals(render1, render2, "Help should be deterministic")
    }

    @Test
    fun `diegetic status contains all state fields`() {
        val state = createTestState()
        val rng = core.rng.Rng(100L)

        val text = DiegeticStatus.render(state, rng)

        assertTrue(text.contains("STEWARD REPORT"), "Should have steward title")
        assertTrue(text.contains("Day ${state.meta.dayIndex}"), "Should contain day")
        assertTrue(text.contains("copper"), "Should contain money info")
        assertTrue(text.contains("stability"), "Should contain stability")
        assertTrue(text.contains("reputation"), "Should contain reputation")
    }

    @Test
    fun `diegetic status shows concerns when applicable`() {
        val state = createTestState().copy(
            region = RegionState(stability = 15)
        )
        val rng = core.rng.Rng(100L)

        val text = DiegeticStatus.render(state, rng)

        assertTrue(text.contains("CONCERNS"), "Should show concerns section")
        assertTrue(text.contains("chaos") || text.contains("brink"), "Should show critical stability concern")
    }

    @Test
    fun `diegetic status shows returns concern`() {
        val state = createTestState().copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                archive = emptyList(),
                active = emptyList(),
                returns = listOf(createTestReturn())
            )
        )
        val rng = core.rng.Rng(100L)

        val text = DiegeticStatus.render(state, rng)

        assertTrue(text.contains("signature") || text.contains("returning"))
    }

    @Test
    fun `diegetic status is deterministic for same state`() {
        val state = createTestState()
        val rng1 = core.rng.Rng(100L)
        val rng2 = core.rng.Rng(100L)

        val render1 = DiegeticStatus.render(state, rng1)
        val render2 = DiegeticStatus.render(state, rng2)

        assertEquals(render1, render2, "Status should be deterministic")
    }

    // ========================================================================
    // Feature 4: Contract Story Flavor Tests
    // ========================================================================

    @Test
    fun `contract flavor deterministic for same contract`() {
        val draft = createTestDraft(difficulty = 50, rank = Rank.C)

        val flavor1 = ContractFlavor.forDraft(draft)
        val flavor2 = ContractFlavor.forDraft(draft)

        assertEquals(flavor1, flavor2, "Contract flavor should be deterministic")
    }

    @Test
    fun `high difficulty contract has danger flavor`() {
        val draft = createTestDraft(difficulty = 80, rank = Rank.A)

        val flavor = ContractFlavor.forDraft(draft)

        assertTrue(
            flavor.contains("death-defying") || flavor.contains("bravest"),
            "High difficulty should mention danger"
        )
    }

    @Test
    fun `low difficulty contract has simple flavor`() {
        val draft = createTestDraft(difficulty = 10, rank = Rank.F)

        val flavor = ContractFlavor.forDraft(draft)

        assertTrue(
            flavor.contains("Simple") || flavor.contains("straightforward") || flavor.contains("novice"),
            "Low difficulty should mention simplicity"
        )
    }

    @Test
    fun `hero salvage policy mentioned in flavor`() {
        val draft = createTestDraft(salvage = SalvagePolicy.HERO)

        val flavor = ContractFlavor.forDraft(draft)

        assertTrue(flavor.contains("Heroes claim spoils"))
    }

    @Test
    fun `guild salvage policy mentioned in flavor`() {
        val draft = createTestDraft(salvage = SalvagePolicy.GUILD)

        val flavor = ContractFlavor.forDraft(draft)

        assertTrue(flavor.contains("guild coffers"))
    }

    @Test
    fun `board contract flavor includes fee assessment`() {
        val board = createTestBoardContract(fee = 500, rank = Rank.C) // Very generous

        val flavor = ContractFlavor.forBoard(board)

        assertTrue(flavor.contains("generous") || flavor.contains("Fair"))
    }

    @Test
    fun `contract list renderer includes flavor text`() {
        val state = createTestState().copy(
            contracts = ContractState(
                inbox = listOf(createTestDraft()),
                board = emptyList(),
                archive = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )

        val text = ContractListRenderer.renderInbox(state)

        assertTrue(text.contains("CONTRACT OFFERS"), "Should have title")
        assertTrue(text.contains("»"), "Should have flavor marker")
    }

    @Test
    fun `empty inbox shows appropriate message`() {
        val state = createTestState()

        val text = ContractListRenderer.renderInbox(state)

        assertTrue(text.contains("No new contract offers"))
    }

    // ========================================================================
    // Feature 5: Hero Return Quotes Tests
    // ========================================================================

    @Test
    fun `success quote is deterministic`() {
        val event = createResolvedEvent(Outcome.SUCCESS)
        val hero = createTestHero(name = "Boromir")

        val quote1 = HeroQuotes.forResolution(event, hero)
        val quote2 = HeroQuotes.forResolution(event, hero)

        assertEquals(quote1, quote2, "Quote should be deterministic")
    }

    @Test
    fun `success quote contains expected text`() {
        val event = createResolvedEvent(Outcome.SUCCESS)
        val hero = createTestHero(name = "Legolas")

        val quote = HeroQuotes.forResolution(event, hero)

        assertTrue(quote.contains("Clean job") || quote.contains("Pay received"))
        assertTrue(quote.contains("Legolas"))
    }

    @Test
    fun `partial quote contains expected text`() {
        val event = createResolvedEvent(Outcome.PARTIAL)
        val hero = createTestHero(name = "Gimli")

        val quote = HeroQuotes.forResolution(event, hero)

        assertTrue(quote.contains("better") || quote.contains("done"))
        assertTrue(quote.contains("Gimli"))
    }

    @Test
    fun `fail quote contains expected text`() {
        val event = createResolvedEvent(Outcome.FAIL)
        val hero = createTestHero(name = "Pippin")

        val quote = HeroQuotes.forResolution(event, hero)

        assertTrue(quote.contains("do not speak"))
        assertTrue(quote.contains("Pippin"))
    }

    @Test
    fun `quote uses generic name when hero is null`() {
        val event = createResolvedEvent(Outcome.SUCCESS)

        val quote = HeroQuotes.forResolution(event, null as Hero?)

        assertTrue(quote.contains("The Hero"))
    }

    @Test
    fun `quote with quality varies by trophy count`() {
        val highTrophies = HeroQuotes.quoteWithQuality(Outcome.SUCCESS, trophiesCount = 10)
        val lowTrophies = HeroQuotes.quoteWithQuality(Outcome.SUCCESS, trophiesCount = 0)

        assertTrue(highTrophies.contains("haul") || highTrophies.contains("prospers"))
        assertTrue(lowTrophies.contains("No salvage"))
    }

    // ========================================================================
    // Helper Functions
    // ========================================================================

    private data class GazetteSnapshotInput(
        val day: Int = 1,
        val gold: Int = 100,
        val trophies: Int = 0,
        val stability: Int = 50,
        val boardCount: Int = 0,
        val activeCount: Int = 0,
        val returnsCount: Int = 0
    )

    private fun createGazetteSnapshot(input: GazetteSnapshotInput = GazetteSnapshotInput()) = GazetteSnapshot(
        day = input.day,
        gold = input.gold,
        trophies = input.trophies,
        stability = input.stability,
        boardCount = input.boardCount,
        activeCount = input.activeCount,
        returnsCount = input.returnsCount
    )

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
            archive = emptyList(),
            active = emptyList(),
            returns = emptyList()
        ),
        heroes = HeroState(
            roster = emptyList(),
            arrivalsToday = emptyList()
        )
    )

    private fun createTestDraft(
        id: Int = 1,
        difficulty: Int = 50,
        rank: Rank = Rank.C,
        salvage: SalvagePolicy = SalvagePolicy.GUILD
    ) = ContractDraft(
        id = ContractId(id),
        createdDay = 1,
        nextAutoResolveDay = 8,
        title = "Test Contract",
        rankSuggested = rank,
        feeOffered = 100,
        salvage = salvage,
        baseDifficulty = difficulty,
        proofHint = "Bring proof",
        clientDeposit = 0
    )

    private fun createTestBoardContract(
        id: Int = 1,
        fee: Int = 100,
        rank: Rank = Rank.C,
        difficulty: Int = 50,
        salvage: SalvagePolicy = SalvagePolicy.GUILD
    ) = BoardContract(
        id = ContractId(id),
        postedDay = 1,
        title = "Test Board Contract",
        rank = rank,
        fee = fee,
        salvage = salvage,
        baseDifficulty = difficulty,
        status = BoardStatus.OPEN,
        clientDeposit = 0
    )

    private fun createTestReturn() = ReturnPacket(
        activeContractId = ActiveContractId(1),
        boardContractId = ContractId(1),
        heroIds = listOf(HeroId(1)),
        resolvedDay = 1,
        outcome = Outcome.SUCCESS,
        trophiesCount = 5,
        trophiesQuality = Quality.OK,
        reasonTags = emptyList(),
        requiresPlayerClose = true,
        suspectedTheft = false
    )

    private fun createResolvedEvent(outcome: Outcome) = ContractResolved(
        day = 1,
        revision = 1L,
        cmdId = 1L,
        seq = 1L,
        activeContractId = 1,
        outcome = outcome,
        trophiesCount = 5,
        quality = Quality.OK,
        reasonTags = intArrayOf()
    )

    private fun createTestHero(name: String = "TestHero") = Hero(
        id = HeroId(1),
        name = name,
        rank = Rank.C,
        klass = HeroClass.WARRIOR,
        traits = Traits(greed = 50, honesty = 50, courage = 50),
        status = HeroStatus.AVAILABLE,
        historyCompleted = 0
    )
}
