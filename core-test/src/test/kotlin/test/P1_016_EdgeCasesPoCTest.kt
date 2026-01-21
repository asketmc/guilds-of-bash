package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: Edge Cases PoC Test.
 * Validates edge case behaviors explicitly called out in PoC specification.
 *
 * Edge Cases:
 * - EC_POC_EMPTY_OPEN_BOARD: AdvanceDay without contracts on board
 * - EC_POC_DOUBLE_TAKE: Multiple heroes wanting same contract (tie-break)
 * - EC_POC_PROCESS_TWICE: Attempt to close already-closed return
 */
class P1_016_EdgeCasesPoCTest {

    @Test
    fun `EC_POC_EMPTY_OPEN_BOARD AdvanceDay with empty board`() {
        // GIVEN: State with empty board (no contracts to take)
        var state = initialState(42u)
        val rng = Rng(100L)

        // Initial state has no board contracts
        assertEquals(0, state.contracts.board.size, "Board should be empty initially")

        // WHEN: AdvanceDay without posting any contracts
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        state = result.state

        // THEN: No ContractTaken events should be emitted
        val contractTakenEvents = result.events.filterIsInstance<ContractTaken>()
        assertEquals(0, contractTakenEvents.size, "No contracts should be taken from empty board")

        // State should be valid (no invariant violations)
        assertNoInvariantViolations(result.events, "EC_EMPTY_OPEN_BOARD: No invariant violations expected")

        // Inbox should still be generated (day progression works normally)
        val inboxGeneratedEvents = result.events.filterIsInstance<InboxGenerated>()
        assertEquals(1, inboxGeneratedEvents.size, "Inbox should still be generated on day advance")

        println("✓ EC_POC_EMPTY_OPEN_BOARD: AdvanceDay with empty board handled correctly")
    }

    @Test
    fun `EC_POC_DOUBLE_TAKE deterministic tie-break when multiple heroes want same contract`() {
        // GIVEN: State with 1 board contract and multiple heroes (deterministic tie-break)
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Day 1: Generate inbox and heroes
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state

        // Post exactly 1 contract
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(state, PostContract(inboxId = inboxId, fee = 10, salvage = SalvagePolicy.HERO, cmdId = cmdId++), rng).state

        // Verify: exactly 1 board contract, multiple heroes available
        assertEquals(1, state.contracts.board.size, "Exactly 1 contract on board")
        assertTrue(state.heroes.roster.size >= 2, "Multiple heroes should be available for tie-break scenario")

        // WHEN: AdvanceDay triggers contract-taking logic
        val result = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result.state

        // THEN: Exactly 1 ContractTaken event (not multiple takes of same contract)
        val contractTakenEvents = result.events.filterIsInstance<ContractTaken>()
        assertTrue(contractTakenEvents.size <= 1, "At most 1 contract can be taken (tie-break prevents double-take)")

        if (contractTakenEvents.isNotEmpty()) {
            val takenEvent = contractTakenEvents.first()
            // Verify exactly 1 active contract created
            val activeForBoard = state.contracts.active.filter { it.boardContractId.value == takenEvent.boardContractId }
            assertEquals(1, activeForBoard.size, "Exactly 1 active contract should reference the board contract")

            println("✓ EC_POC_DOUBLE_TAKE: Deterministic tie-break prevents double-take (taken by hero ${takenEvent.heroIds.contentToString()})")
        } else {
            println("⚠ EC_POC_DOUBLE_TAKE: No contract taken (possibly all heroes declined)")
        }

        // No invariant violations
        assertNoInvariantViolations(result.events, "EC_DOUBLE_TAKE: No invariant violations expected")
    }

    @Test
    fun `EC_POC_PROCESS_TWICE attempt to close already-closed return`() {
        // GIVEN: Scenario where return is closed, then attempt to close again
        var state = initialState(42u)
        val rng = Rng(300L)  // Seed for PARTIAL outcome requiring player close
        var cmdId = 1L

        // Full flow: post → take → resolve → close
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(state, PostContract(inboxId = inboxId, fee = 100, salvage = SalvagePolicy.HERO, cmdId = cmdId++), rng).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state  // Take + advance
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state  // Resolve

        // Verify return was created
        val returnsRequiringClose = state.contracts.returns.filter { it.requiresPlayerClose }
        if (returnsRequiringClose.isEmpty()) {
            println("⚠ EC_POC_PROCESS_TWICE: Skipping test (no returns requiring close with this seed)")
            return
        }

        val returnPacket = returnsRequiringClose.first()
        val activeId = returnPacket.activeContractId.value.toLong()

        // WHEN: Close return successfully (first time)
        val result1 = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng)
        state = result1.state

        // Verify first close succeeded
        val returnClosedEvents = result1.events.filterIsInstance<ReturnClosed>()
        assertEquals(1, returnClosedEvents.size, "First close should succeed")

        // THEN: Attempt to close same return again (second time)
        val result2 = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng)

        // Expected: CommandRejected with NOT_FOUND (return no longer exists after close)
        assertSingleRejection(result2.events, RejectReason.NOT_FOUND,
            "EC_PROCESS_TWICE: Second close attempt should be rejected (return already closed)")

        // State unchanged after rejection
        assertEquals(state, result2.state, "State should be unchanged after rejected command")

        // No invariant violations
        assertNoInvariantViolations(result2.events, "EC_PROCESS_TWICE: No invariant violations expected")

        println("✓ EC_POC_PROCESS_TWICE: Second close attempt correctly rejected")
    }

    @Test
    fun `edge case multiple AdvanceDay calls in sequence without player commands`() {
        // GIVEN: Initial state
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // WHEN: Multiple AdvanceDay commands in sequence (no player interaction)
        repeat(5) { iteration ->
            val result = step(state, AdvanceDay(cmdId = cmdId++), rng)
            state = result.state

            // THEN: Each day should succeed without errors
            assertNoRejections(result.events, "Day $iteration should succeed")
            assertNoInvariantViolations(result.events, "Day $iteration should have no invariant violations")
        }

        // Final state should be day 5
        assertEquals(5, state.meta.dayIndex, "Should advance to day 5")

        // Inbox should accumulate (contracts not auto-removed)
        assertTrue(state.contracts.inbox.size >= 5, "Inbox should accumulate contracts from each day")

        println("✓ Edge case: Multiple AdvanceDay calls handled correctly (day ${state.meta.dayIndex})")
    }

    @Test
    fun `edge case post all inbox contracts then AdvanceDay`() {
        // GIVEN: State with full inbox
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Generate inbox
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        val inboxSize = state.contracts.inbox.size
        assertTrue(inboxSize >= 2, "Need at least 2 inbox contracts for this test")

        // WHEN: Post all inbox contracts to board
        val inboxIds = state.contracts.inbox.map { it.id.value.toLong() }
        for (id in inboxIds) {
            val result = step(state, PostContract(inboxId = id, fee = 5, salvage = SalvagePolicy.GUILD, cmdId = cmdId++), rng)
            state = result.state

            // All posts should succeed (enough money)
            assertNoRejections(result.events, "Post inboxId=$id should succeed")
        }

        // Verify all moved to board
        assertEquals(0, state.contracts.inbox.size, "Inbox should be empty after posting all")
        assertEquals(inboxSize, state.contracts.board.size, "Board should have all posted contracts")

        // THEN: AdvanceDay with full board (heroes take contracts)
        val result = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result.state

        // Some contracts should be taken (depends on hero availability and attractiveness)
        val contractsTaken = result.events.filterIsInstance<ContractTaken>().size
        println("✓ Edge case: Posted $inboxSize contracts, $contractsTaken taken by heroes")

        // No invariant violations
        assertNoInvariantViolations(result.events, "Full board AdvanceDay should not violate invariants")
    }

    @Test
    fun `edge case sell trophies with amount exceeding stock`() {
        // GIVEN: State with some trophies
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Create trophies via full contract flow
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(state, PostContract(inboxId = inboxId, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = cmdId++), rng).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state  // Take
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state  // Resolve

        // Close return to deposit trophies (if PARTIAL outcome)
        val returnsRequiringClose = state.contracts.returns.filter { it.requiresPlayerClose }
        if (returnsRequiringClose.isNotEmpty()) {
            val activeId = returnsRequiringClose.first().activeContractId.value.toLong()
            state = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng).state
        }

        val trophyStock = state.economy.trophiesStock
        if (trophyStock == 0) {
            println("⚠ Edge case: Skipping test (no trophies in stock with this seed)")
            return
        }

        // WHEN: Attempt to sell more trophies than available
        val excessiveAmount = trophyStock + 100
        val result = step(state, SellTrophies(amount = excessiveAmount, cmdId = cmdId++), rng)
        state = result.state

        // THEN: Should sell only available stock (not reject)
        assertNoRejections(result.events, "SellTrophies should not reject on excessive amount")

        val trophySoldEvents = result.events.filterIsInstance<TrophySold>()
        if (trophySoldEvents.isNotEmpty()) {
            val soldAmount = trophySoldEvents.first().amount
            assertTrue(soldAmount <= trophyStock, "Should sell at most available stock")
            assertEquals(0, state.economy.trophiesStock, "All available trophies should be sold")
            println("✓ Edge case: Requested $excessiveAmount, sold $soldAmount (stock was $trophyStock)")
        }

        assertNoInvariantViolations(result.events, "Sell with excessive amount should not violate invariants")
    }
}
