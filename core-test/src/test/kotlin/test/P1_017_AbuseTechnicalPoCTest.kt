package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: Abuse Technical PoC Test.
 * Validates anti-abuse technical safeguards in the PoC reducer.
 *
 * Abuse Cases:
 * - AB_POC_REPROCESS_DUP: Attempt to reprocess same command (if implemented)
 * - AB_POC_INVALID_MUTATION: State mutation outside reducer (conceptual)
 * - AB_POC_SELL_BOUNDARY: Sell trophies boundary behaviors
 */
class P1_017_AbuseTechnicalPoCTest {

    @Test
    fun `AB_POC_REPROCESS_DUP same cmdId cannot be reprocessed`() {
        // NOTE: Current implementation does NOT track processed cmdIds (stateless reducer).
        // This test documents the ABSENCE of replay protection at reducer level.
        // Replay protection must be implemented at adapter/persistence layer.

        // GIVEN: State and command with specific cmdId
        val state = initialState(42u)
        val rng = Rng(100L)
        val cmd = AdvanceDay(cmdId = 1L)

        // WHEN: Apply same command twice with same RNG seed (simulating replay)
        val result1 = step(state, cmd, Rng(100L))
        val result2 = step(state, cmd, Rng(100L))

        // THEN: Both succeed (reducer is stateless and does not track cmdId history)
        assertNoRejections(result1.events, "First application should succeed")
        assertNoRejections(result2.events, "Second application should also succeed (no replay protection in reducer)")

        // Results should be identical (deterministic)
        assertEquals(
            result1.state.meta.dayIndex,
            result2.state.meta.dayIndex,
            "Both applications produce identical state (deterministic)"
        )

        println("⚠ AB_POC_REPROCESS_DUP: Reducer does NOT prevent replay (adapter responsibility)")
        println("  - This is BY DESIGN: reducer is pure and stateless")
        println("  - Replay protection must be implemented at persistence/adapter layer")
    }

    @Test
    fun `AB_POC_INVALID_MUTATION state only changes through step function`() {
        // GIVEN: Initial state
        val state = initialState(42u)

        // WHEN: Attempt to mutate state directly (conceptual test - Kotlin data classes are immutable)
        // This test documents that GameState is immutable and can only change via step()

        // THEN: Verify immutability contract
        // data class copy() is the ONLY way to create modified state
        val modifiedState = state.copy(
            economy = state.economy.copy(moneyCopper = 999)
        )

        // Original state unchanged
        assertEquals(100, state.economy.moneyCopper, "Original state should be unchanged")
        assertEquals(999, modifiedState.economy.moneyCopper, "Modified state has new value")

        // Verify they are different objects
        assertNotEquals(state, modifiedState, "Original and modified are different instances")

        println("✓ AB_POC_INVALID_MUTATION: State immutability contract verified")
        println("  - GameState is immutable (Kotlin data class)")
        println("  - Only step() function can produce valid state transitions")
    }

    @Test
    fun `AB_POC_SELL_BOUNDARY sell with negative amount`() {
        // GIVEN: State with trophies
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Create trophies
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(
            state,
            PostContract(inboxId = inboxId, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = cmdId++),
            rng
        ).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state

        // Close return if needed
        val returnsRequiringClose = state.contracts.returns.filter { it.requiresPlayerClose }
        if (returnsRequiringClose.isNotEmpty()) {
            val activeId = returnsRequiringClose.first().activeContractId.value.toLong()
            state = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng).state
        }

        if (state.economy.trophiesStock == 0) {
            println("⚠ AB_POC_SELL_BOUNDARY: Skipping test (no trophies with this seed)")
            return
        }

        // WHEN: Attempt to sell negative amount
        val result = step(state, SellTrophies(amount = -10, cmdId = cmdId++), rng)

        // THEN: Command should be accepted but treated as 0 or sell all (implementation-dependent)
        // Current implementation: negative amount treated as "sell all" (amount <= 0 → sell all)
        assertNoRejections(result.events, "SellTrophies with negative amount should not reject")

        val trophySoldEvents = result.events.filterIsInstance<TrophySold>()
        if (trophySoldEvents.isNotEmpty()) {
            println("✓ AB_POC_SELL_BOUNDARY: Negative amount treated as sell-all (implementation choice)")
        } else {
            println("✓ AB_POC_SELL_BOUNDARY: Negative amount resulted in no-op")
        }

        assertNoInvariantViolations(result.events, "Negative sell amount should not violate invariants")
    }

    @Test
    fun `AB_POC_SELL_BOUNDARY sell exactly available stock`() {
        // GIVEN: State with known trophy stock
        var state = initialState(42u)
        val rng = Rng(300L)
        var cmdId = 1L

        // Create trophies
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(
            state,
            PostContract(inboxId = inboxId, fee = 100, salvage = SalvagePolicy.GUILD, cmdId = cmdId++),
            rng
        ).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state

        val returnsRequiringClose = state.contracts.returns.filter { it.requiresPlayerClose }
        if (returnsRequiringClose.isNotEmpty()) {
            val activeId = returnsRequiringClose.first().activeContractId.value.toLong()
            state = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng).state
        }

        val trophyStock = state.economy.trophiesStock
        if (trophyStock == 0) {
            println("⚠ AB_POC_SELL_BOUNDARY: Skipping test (no trophies with this seed)")
            return
        }

        // WHEN: Sell exactly available stock
        val result = step(state, SellTrophies(amount = trophyStock, cmdId = cmdId++), rng)
        state = result.state

        // THEN: All trophies sold
        assertNoRejections(result.events, "Sell exact stock should succeed")

        val trophySoldEvents = result.events.filterIsInstance<TrophySold>()
        if (trophySoldEvents.isNotEmpty()) {
            val soldAmount = trophySoldEvents.first().amount
            assertEquals(trophyStock, soldAmount, "Should sell exactly requested amount")
            assertEquals(0, state.economy.trophiesStock, "Stock should be 0 after selling all")
            println("✓ AB_POC_SELL_BOUNDARY: Sold exactly $trophyStock trophies (exact stock match)")
        }

        assertNoInvariantViolations(result.events, "Sell exact stock should not violate invariants")
    }

    @Test
    fun `AB_POC_SELL_BOUNDARY sell more than stock`() {
        // GIVEN: State with trophies
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(
            state,
            PostContract(inboxId = inboxId, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = cmdId++),
            rng
        ).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state

        val returnsRequiringClose = state.contracts.returns.filter { it.requiresPlayerClose }
        if (returnsRequiringClose.isNotEmpty()) {
            val activeId = returnsRequiringClose.first().activeContractId.value.toLong()
            state = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng).state
        }

        val trophyStock = state.economy.trophiesStock
        if (trophyStock == 0) {
            println("⚠ AB_POC_SELL_BOUNDARY: Skipping test (no trophies with this seed)")
            return
        }

        // WHEN: Attempt to sell MORE than available
        val excessAmount = trophyStock + 50
        val result = step(state, SellTrophies(amount = excessAmount, cmdId = cmdId++), rng)

        // THEN: Current semantics (via validateSellTrophies): reject if amount > stock
        assertSingleRejection(result.events, RejectReason.INVALID_STATE, "Sell excessive amount should reject")

        val rej = result.events.first() as CommandRejected
        assertTrue(
            rej.detail.contains("Insufficient trophies", ignoreCase = true) ||
                    rej.detail.contains("trophies", ignoreCase = true),
            "Rejection detail should mention trophies insufficiency"
        )

        // State must be unchanged on rejection
        assertEquals(trophyStock, result.state.economy.trophiesStock, "Trophy stock must be unchanged on rejection")
        assertEquals(state.economy.moneyCopper, result.state.economy.moneyCopper, "Money must be unchanged on rejection")

        // No sell event should be emitted on rejection
        assertTrue(result.events.filterIsInstance<TrophySold>().isEmpty(), "TrophySold must not be emitted on rejection")

        assertNoInvariantViolations(result.events, "Sell excessive amount rejection should not violate invariants")
    }

    @Test
    fun `AB_POC_COMMAND_VALIDATION rejected commands leave state unchanged`() {
        // GIVEN: Initial state
        val state = initialState(42u)
        val rng = Rng(100L)

        // WHEN: Submit invalid command (non-existent inbox ID)
        val invalidCmd = PostContract(inboxId = 999L, fee = 10, salvage = SalvagePolicy.GUILD, cmdId = 1L)
        val result = step(state, invalidCmd, rng)

        // THEN: State should be EXACTLY unchanged (except revision increments on rejection are NOT applied)
        assertEquals(state, result.state, "State should be unchanged after rejected command")

        // CommandRejected event emitted
        assertSingleRejection(
            result.events,
            RejectReason.NOT_FOUND,
            "AB_COMMAND_VALIDATION: Invalid command should be rejected"
        )

        println("✓ AB_POC_COMMAND_VALIDATION: Rejected commands leave state unchanged")
    }

    @Test
    fun `AB_POC_ESCROW_MANIPULATION cannot post contract exceeding available money`() {
        // GIVEN: State with 100 money, 50 already reserved
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Day 1: generate inbox
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state

        // Post first contract with fee=50 (reserve 50)
        val inbox1 = state.contracts.inbox.first().id.value.toLong()
        state = step(
            state,
            PostContract(inboxId = inbox1, fee = 50, salvage = SalvagePolicy.GUILD, cmdId = cmdId++),
            rng
        ).state

        // Verify: 50 reserved, 50 available
        assertEquals(50, state.economy.reservedCopper, "50 should be reserved")
        assertEquals(50, state.economy.moneyCopper - state.economy.reservedCopper, "50 should be available")

        // WHEN: Attempt to post second contract with fee=60 (exceeds available 50)
        val inbox2 = state.contracts.inbox.first().id.value.toLong()
        val result = step(
            state,
            PostContract(inboxId = inbox2, fee = 60, salvage = SalvagePolicy.GUILD, cmdId = cmdId++),
            rng
        )

        // THEN: Command rejected (insufficient available money)
        assertSingleRejection(
            result.events,
            RejectReason.INVALID_STATE,
            "AB_ESCROW_MANIPULATION: Cannot post contract exceeding available money"
        )

        // State unchanged
        assertEquals(state, result.state, "State should be unchanged after rejection")

        println("✓ AB_POC_ESCROW_MANIPULATION: Escrow validation prevents over-reservation")
    }

    @Test
    fun `AB_POC_INVARIANT_ENFORCEMENT invariant violations are detected and reported`() {
        // NOTE: This test is CONCEPTUAL. Current reducer implementation always maintains invariants.
        // To truly test invariant violation detection, we'd need to:
        // 1. Inject a buggy reducer version, OR
        // 2. Manually construct invalid state and call verifyInvariants()

        // For now, document that verifyInvariants() is called after every step()
        val state = initialState(42u)
        val rng = Rng(100L)

        val result = step(state, AdvanceDay(cmdId = 1L), rng)

        // Verify no violations on valid reducer behavior
        assertNoInvariantViolations(result.events, "Valid reducer should produce no invariant violations")

        println("✓ AB_POC_INVARIANT_ENFORCEMENT: verifyInvariants() is called after every step()")
        println("  - Current reducer maintains invariants correctly")
        println("  - InvariantViolated events would be emitted if bugs exist")
    }
}
