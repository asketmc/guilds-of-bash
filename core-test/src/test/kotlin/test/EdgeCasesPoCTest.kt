package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.SalvagePolicy
import test.helpers.advanceDay
import test.helpers.assertEventCount
import test.helpers.assertNoInvariantViolations
import test.helpers.assertNoRejections
import test.helpers.assertSingleRejection
import test.helpers.assertStepDeterministic
import test.helpers.closeReturn
import test.helpers.postContractFromInbox
import test.helpers.requireReturnRequiringClose
import test.helpers.requireTrophyStockPositive
import test.helpers.run
import test.helpers.sellTrophies
import test.helpers.session
import kotlin.test.*

/**
 * P2: Edge Cases PoC Test.
 * Validates edge case behaviors explicitly called out in PoC specification.
 *
 * Edge Cases:
 * - EC_POC_EMPTY_OPEN_BOARD: AdvanceDay without contracts on board
 * - EC_POC_DOUBLE_TAKE: Multiple heroes wanting same contract (tie-break)
 * - EC_POC_PROCESS_TWICE: Attempt to close already-closed return
 */
@P2
class EdgeCasesPoCTest {

    @Test
    fun `EC_POC_EMPTY_OPEN_BOARD AdvanceDay with empty board`() {
        val s = session(stateSeed = 42u, rngSeed = 100L)

        assertEquals(0, s.state.contracts.board.size, "Board should be empty initially")

        val r = s.advanceDay()

        assertEventCount<ContractTaken>(
            r.events,
            expected = 0,
            message = "No contracts should be taken from empty board"
        )
        assertNoInvariantViolations(r.events, "EC_EMPTY_OPEN_BOARD: No invariant violations expected")
        assertEventCount<InboxGenerated>(
            r.events,
            expected = 1,
            message = "Inbox should still be generated on day advance"
        )
    }

    @Test
    fun `EC_POC_DOUBLE_TAKE deterministic tie-break when multiple heroes want same contract`() {
        val s = session(stateSeed = 42u, rngSeed = 100L)

        // Day 1: Generate inbox and heroes
        s.advanceDay()

        // Post exactly 1 contract
        s.postContractFromInbox(inboxIndex = 0, fee = 10, salvage = SalvagePolicy.HERO)

        assertEquals(1, s.state.contracts.board.size, "Exactly 1 contract on board")
        assertTrue(s.state.heroes.roster.size >= 2, "Multiple heroes should be available for tie-break scenario")

        // Determinism check for this single step:
        // (same state snapshot + same cmd + same rng seed => identical hashed outputs)
        val cmd = AdvanceDay(cmdId = s.cmdId) // DO NOT increment; command must be identical for both runs
        assertStepDeterministic(
            state = s.state,
            cmd = cmd,
            rngSeed = 100L,
            message = "EC_DOUBLE_TAKE: AdvanceDay must be deterministic for fixed state/cmd/seed"
        )

        // Execute the step once for semantic assertions
        val r = s.advanceDay()

        val taken = r.events.filterIsInstance<ContractTaken>()
        assertTrue(taken.size <= 1, "At most 1 contract can be taken (tie-break prevents double-take)")

        if (taken.isNotEmpty()) {
            val takenEvent = taken.first()
            val activeForBoard = s.state.contracts.active.filter { it.boardContractId.value == takenEvent.boardContractId }
            assertEquals(1, activeForBoard.size, "Exactly 1 active contract should reference the board contract")
        }

        assertNoInvariantViolations(r.events, "EC_DOUBLE_TAKE: No invariant violations expected")
    }

    @Test
    fun `EC_POC_PROCESS_TWICE attempt to close already-closed return`() {
        val s = session(stateSeed = 42u, rngSeed = 300L)

        // post → take → resolve → close → close again (expects NOT_FOUND on second close)
        s.advanceDay()
        s.postContractFromInbox(inboxIndex = 0, fee = 100, salvage = SalvagePolicy.HERO)
        s.advanceDay()
        s.advanceDay()

        val rp = requireReturnRequiringClose(
            state = s.state,
            message = "EC_PROCESS_TWICE: expected requiresPlayerClose=true for this seed/flow"
        )
        val activeId = rp.activeContractId.value.toLong()

        val r1 = s.closeReturn(activeContractId = activeId)
        assertEventCount<ReturnClosed>(r1.events, expected = 1, message = "First close should succeed")

        val r2 = s.closeReturn(activeContractId = activeId)
        assertSingleRejection(
            r2.events,
            RejectReason.NOT_FOUND,
            "EC_PROCESS_TWICE: Second close attempt should be rejected (return already closed)"
        )
        assertEquals(s.state, r2.state, "State should be unchanged after rejected command")
        assertNoInvariantViolations(r2.events, "EC_PROCESS_TWICE: No invariant violations expected")
    }

    @Test
    fun `edge case multiple AdvanceDay calls in sequence without player commands`() {
        val s = session(stateSeed = 42u, rngSeed = 100L)

        repeat(5) { iteration ->
            val r = s.advanceDay()
            assertNoRejections(r.events, "Day $iteration should succeed")
            assertNoInvariantViolations(r.events, "Day $iteration should have no invariant violations")
        }

        assertEquals(5, s.state.meta.dayIndex, "Should advance to day 5")
        assertTrue(s.state.contracts.inbox.size >= 5, "Inbox should accumulate contracts from each day")
    }

    @Test
    fun `edge case post all inbox contracts then AdvanceDay`() {
        val s = session(stateSeed = 42u, rngSeed = 100L)

        s.advanceDay()
        val inboxSize = s.state.contracts.inbox.size
        assertTrue(inboxSize >= 2, "Need at least 2 inbox contracts for this test")

        val inboxIds = s.state.contracts.inbox.map { it.id.value.toLong() }
        for (id in inboxIds) {
            val r = s.run(PostContract(inboxId = id, fee = 5, salvage = SalvagePolicy.GUILD, cmdId = s.cmdId++))
            assertNoRejections(r.events, "Post inboxId=$id should succeed")
        }

        assertEquals(0, s.state.contracts.inbox.size, "Inbox should be empty after posting all")
        assertEquals(inboxSize, s.state.contracts.board.size, "Board should have all posted contracts")

        val r = s.advanceDay()
        assertNoInvariantViolations(r.events, "Full board AdvanceDay should not violate invariants")
    }

    @Test
    fun `edge case sell trophies with amount exceeding stock`() {
        val s = session(stateSeed = 42u, rngSeed = 100L)

        // Create trophies via flow (may or may not end up in guild stock depending on outcome + close)
        s.advanceDay()
        s.postContractFromInbox(inboxIndex = 0, fee = 10, salvage = SalvagePolicy.GUILD)
        s.advanceDay()
        s.advanceDay()

        val returnsRequiringClose = s.state.contracts.returns.filter { it.requiresPlayerClose }
        if (returnsRequiringClose.isNotEmpty()) {
            val activeId = returnsRequiringClose.first().activeContractId.value.toLong()
            s.closeReturn(activeContractId = activeId)
        }

        val trophyStock = requireTrophyStockPositive(
            state = s.state,
            message = "SellTrophies(amount > stock) test requires deterministic trophiesStock>0 setup"
        )

        val excessiveAmount = trophyStock + 100
        val r = s.sellTrophies(amount = excessiveAmount)

        assertSingleRejection(
            r.events,
            RejectReason.INVALID_STATE,
            "SellTrophies(amount > stock) should be rejected by validation"
        )
        assertEquals(s.state, r.state, "State should be unchanged after rejected SellTrophies")
        assertNoInvariantViolations(r.events, "Rejected SellTrophies should not violate invariants")

        val sellAll = s.sellTrophies(amount = 0)
        assertNoRejections(sellAll.events, "SellTrophies(amount=0) should not reject")
        assertNoInvariantViolations(sellAll.events, "Sell-all should not violate invariants")
    }
}
