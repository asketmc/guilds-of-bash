package test

import core.*
import core.primitives.*
import core.rng.Rng
import core.state.*
import test.helpers.*
import kotlin.test.*

/**
 * P2: Contract Expiry (Auto-Resolve) PoC Tests
 *
 * Tests contract auto-resolution when drafts reach their expiry deadline in inbox.
 * Auto-resolve runs during AdvanceDay after InboxGenerated/HeroesArrived.
 *
 * Rules:
 * - Only inbox drafts participate (board/active excluded)
 * - When dayIndex >= draft.nextAutoResolveDay, draft is processed
 * - RNG determines bucket: GOOD (removed), NEUTRAL (kept, +7 days), BAD (removed, stability -2)
 * - One contract resolves at most once per day tick
 */
@P2
class ContractExpiryPoCTest {

    @Test
    fun `not due - no auto resolve events and draft stays`() {
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 10),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(1),
                        createdDay = 8,
                        nextAutoResolveDay = 15,
                        title = "Draft #1",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        // ScriptedRng must include values for: inbox generation (2 drafts with baseDifficulty + payout + clientPaysRoll each), hero name picks (2 heroes), then anything else.
        // Not-due path doesn't consume expiry bucket.
        // Per draft: baseDifficulty(1), payout(1), clientPaysRoll(1) = 3 draws
        // 2 drafts = 6 draws, then 2 hero names = 2 draws
        val rng = ScriptedRng(
            0, 0, 50, // draft 1: baseDifficulty, payout, clientPaysRoll (50 >= 50 means no deposit)
            0, 0, 50, // draft 2: baseDifficulty, payout, clientPaysRoll
            0, 0      // hero name picks (2 heroes)
        )

        val r = step(state, AdvanceDay(cmdId = 1L), rng)

        // Draft should stay in inbox
        assertTrue(r.state.contracts.inbox.any { it.id.value == 1 }, "Expected inbox to contain draftId=1")

        // No auto-resolve events
        val autoEvents = r.events.filter { it::class.simpleName == "ContractAutoResolved" }
        assertEquals(0, autoEvents.size, "Expected no ContractAutoResolved events")
    }

    @Test
    fun `due - GOOD closes draft`() {
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 14),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(1),
                        createdDay = 1,
                        nextAutoResolveDay = 15,
                        title = "Draft #1",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        // Script: [per draft: baseDifficulty, payout, clientPaysRoll] x2, heroName x2, bucket=GOOD(0)
        val rng = ScriptedRng(
            0, 0, 50, // draft 1
            0, 0, 50, // draft 2
            0, 0,     // hero names
            0         // bucket=GOOD
        )

        val r = step(state, AdvanceDay(cmdId = 1L), rng)

        // Draft should be removed
        assertTrue(r.state.contracts.inbox.none { it.id.value == 1 }, "Expected inbox NOT to contain draftId=1")

        // ContractAutoResolved event
        val auto = r.events.filter { it::class.simpleName == "ContractAutoResolved" }
        assertEquals(1, auto.size, "Expected exactly 1 ContractAutoResolved")

        // No stability change
        val stab = r.events.filterIsInstance<StabilityUpdated>()
        assertEquals(0, stab.size, "Expected no StabilityUpdated on GOOD")
    }

    @Test
    fun `due - NEUTRAL keeps draft and schedules next week`() {
        // Script: [per draft: baseDifficulty, payout, clientPaysRoll] x2, heroName x2, bucket=NEUTRAL(1)
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 14),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(1),
                        createdDay = 1,
                        nextAutoResolveDay = 15,
                        title = "Draft #1",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        val rng = ScriptedRng(
            0, 0, 50, // draft 1
            0, 0, 50, // draft 2
            0, 0,     // hero names
            1         // bucket=NEUTRAL
        )

        val r = step(state, AdvanceDay(cmdId = 1L), rng)

        // Draft should stay in inbox
        assertTrue(r.state.contracts.inbox.any { it.id.value == 1 }, "Expected inbox to contain draftId=1")

        // ContractAutoResolved event
        val auto = r.events.filter { it::class.simpleName == "ContractAutoResolved" }
        assertEquals(1, auto.size, "Expected exactly 1 ContractAutoResolved")

        // nextAutoResolveDay should be 22 (15 + 7)
        val d1 = r.state.contracts.inbox.single { it.id.value == 1 }
        assertEquals(22, d1.nextAutoResolveDay, "Expected nextAutoResolveDay to shift by +7 from 15 to 22")

        // No stability change
        val stab = r.events.filterIsInstance<StabilityUpdated>()
        assertEquals(0, stab.size, "Expected no StabilityUpdated on NEUTRAL")
    }

    @Test
    fun `due - BAD closes and applies stability delta`() {
        // GIVEN
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 14),
            region = RegionState(stability = 50),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(1),
                        createdDay = 1,
                        nextAutoResolveDay = 15,
                        title = "Draft #1",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        // Script: [per draft: baseDifficulty, payout, clientPaysRoll] x2, heroName x2, bucket=BAD(2)
        val rng = ScriptedRng(
            0, 0, 50, // draft 1
            0, 0, 50, // draft 2
            0, 0,     // hero names
            2         // bucket=BAD
        )

        // WHEN
        val r = step(state, AdvanceDay(cmdId = 1L), rng)

        // THEN
        assertTrue(r.state.contracts.inbox.none { it.id.value == 1 }, "Expected inbox NOT to contain draftId=1")
        val auto = r.events.filter { it::class.simpleName == "ContractAutoResolved" }
        assertEquals(1, auto.size, "Expected exactly 1 ContractAutoResolved")
        assertEquals(48, r.state.region.stability, "Expected stability to decrease by 2 on BAD")

        val stab = r.events.filterIsInstance<StabilityUpdated>()
        assertEquals(1, stab.size, "Expected exactly 1 StabilityUpdated on BAD")
        val ev = stab.single()
        assertEquals(50, ev.oldStability)
        assertEquals(48, ev.newStability)
    }

    @Test
    fun `cadence - NEUTRAL triggers only weekly not daily`() {
        // GIVEN
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 14),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(1),
                        createdDay = 1,
                        nextAutoResolveDay = 15,
                        title = "Draft #1",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        // Day1 script: [per draft: baseDifficulty, payout, clientPaysRoll] x2, heroName x2, bucket=NEUTRAL(1)
        // Day2 script: [per draft: baseDifficulty, payout, clientPaysRoll] x2, heroName x2 (not due => no bucket)
        val rng = ScriptedRng(
            // Day 1
            0, 0, 50, // draft 1
            0, 0, 50, // draft 2
            0, 0,     // hero names
            1,        // bucket=NEUTRAL
            // Day 2
            0, 0, 50, // draft 1
            0, 0, 50, // draft 2
            0, 0      // hero names
        )

        // WHEN
        val r1 = step(state, AdvanceDay(cmdId = 1L), rng)
        val d1 = r1.state.contracts.inbox.single { it.id.value == 1 }
        assertEquals(22, d1.nextAutoResolveDay)

        val r2 = step(r1.state, AdvanceDay(cmdId = 2L), rng)

        // THEN
        assertEquals(
            0,
            r2.events.filter { it::class.simpleName == "ContractAutoResolved" }.size,
            "Expected no ContractAutoResolved when not due (day 16)"
        )
    }

    @Test
    fun `multiple due drafts - deterministic processing order equals inbox list order`() {
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 14),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(10),
                        createdDay = 0,
                        nextAutoResolveDay = 15,
                        title = "Draft #10",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    ),
                    ContractDraft(
                        id = ContractId(11),
                        createdDay = 0,
                        nextAutoResolveDay = 15,
                        title = "Draft #11",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    ),
                    ContractDraft(
                        id = ContractId(12),
                        createdDay = 0,
                        nextAutoResolveDay = 15,
                        title = "Draft #12",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        // Script: [per draft: baseDifficulty, payout, clientPaysRoll] x2, heroName x2, bucket1, bucket2, bucket3
        val rng = ScriptedRng(
            0, 0, 50, // draft 1
            0, 0, 50, // draft 2
            0, 0,     // hero names
            0, 1, 2   // buckets for 3 due drafts: GOOD, NEUTRAL, BAD
        )

        val r = step(state, AdvanceDay(cmdId = 1L), rng)

        val auto = r.events.filter { it::class.simpleName == "ContractAutoResolved" }
        assertEquals(3, auto.size, "Expected 3 ContractAutoResolved events")

        // Assert processing order matches inbox order
        val asStrings = auto.map { it.toString() }
        assertTrue(asStrings[0].contains("draftId=10"), "Expected first auto-resolve to reference draftId=10 but was ${asStrings[0]}")
        assertTrue(asStrings[1].contains("draftId=11"), "Expected second auto-resolve to reference draftId=11 but was ${asStrings[1]}")
        assertTrue(asStrings[2].contains("draftId=12"), "Expected third auto-resolve to reference draftId=12 but was ${asStrings[2]}")
    }

    @Test
    fun `exclusion - board and active are not auto resolved`() {
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 14),
            contracts = ContractState(
                inbox = emptyList(),
                board = listOf(
                    BoardContract(
                        id = ContractId(1),
                        postedDay = 0,
                        title = "Board",
                        rank = Rank.F,
                        fee = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        status = BoardStatus.OPEN
                    )
                ),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 0,
                        daysRemaining = 10,
                        heroIds = emptyList(),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            )
        )

        // Capture IDs BEFORE AdvanceDay
        val boardIdsBefore = state.contracts.board.map { it.id.value }.toSet()
        val activeIdsBefore = state.contracts.active.map { it.id.value }.toSet()

        val rng = Rng(100L)

        val r = step(state, AdvanceDay(cmdId = 1L), rng)

        // Assert no ContractAutoResolved events
        assertEquals(0, r.events.filter { it::class.simpleName == "ContractAutoResolved" }.size,
            "Expected no ContractAutoResolved events for board/active contracts")

        // Capture IDs AFTER AdvanceDay
        val boardIdsAfter = r.state.contracts.board.map { it.id.value }.toSet()
        val activeIdsAfter = r.state.contracts.active.map { it.id.value }.toSet()

        // Assert original board/active IDs are preserved (expiry must not touch them)
        assertTrue(boardIdsBefore.all { it in boardIdsAfter },
            "Expected all original board IDs to be preserved after AdvanceDay")
        assertTrue(activeIdsBefore.all { it in activeIdsAfter },
            "Expected all original active IDs to be preserved after AdvanceDay")
    }

    @Test
    fun `interaction with InboxGenerated - newly generated drafts not immediately auto resolved`() {
        // Day 15: inbox generation creates drafts with nextAutoResolveDay = 22
        // Auto-resolve should not process them on the same day
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 14),
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        val rng = Rng(100L)

        val r = step(state, AdvanceDay(cmdId = 1L), rng)

        // New drafts should be in inbox
        assertTrue(r.state.contracts.inbox.isNotEmpty(), "Expected InboxGenerated to create drafts")

        // No auto-resolve events for newly created drafts
        val auto = r.events.filter { it::class.simpleName == "ContractAutoResolved" }
        assertEquals(0, auto.size, "Expected no ContractAutoResolved for newly generated drafts")
    }

    @Test
    fun `CommandRejected behavior unchanged - no auto resolve on rejected commands`() {
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(dayIndex = 14),
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(1),
                        createdDay = 1,
                        nextAutoResolveDay = 15,
                        title = "Draft #1",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        salvage = SalvagePolicy.GUILD,
                        baseDifficulty = 1,
                        proofHint = "hint"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            )
        )
        // RNG won't be used for rejected commands
        val rng = ScriptedRng()

        // Invalid command (non-existent inbox ID)
        val r = step(state, PostContract(inboxId = 999L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 1L), rng)

        // Only CommandRejected event
        assertEquals(1, r.events.size, "Expected only 1 event")
        assertTrue(r.events[0] is CommandRejected, "Expected CommandRejected event")

        // No auto-resolve
        val auto = r.events.filter { it::class.simpleName == "ContractAutoResolved" }
        assertEquals(0, auto.size, "Expected no ContractAutoResolved on rejected command")

        // State unchanged
        assertEquals(state, r.state, "State should be unchanged on rejected command")
    }
}
