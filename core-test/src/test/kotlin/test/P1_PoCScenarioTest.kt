package test

import core.*
import core.hash.hashState
import core.invariants.verifyInvariants
import core.primitives.*
import core.rng.Rng
import core.state.initialState
import kotlin.test.*

/**
 * P1 CRITICAL: PoC end-to-end scenario tests.
 * Validates the complete micro-game loop from contracts to trophies to money.
 */
class P1_PoCScenarioTest {

    @Test
    fun `poc scenario basic contract flow end to end`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Initial state verification
        assertEquals(100, state.economy.moneyCopper, "Initial money should be 100")
        assertEquals(0, state.economy.trophiesStock, "Initial trophies should be 0")
        assertEquals(50, state.region.stability, "Initial stability should be 50")
        assertEquals(0, state.contracts.inbox.size, "Initial inbox should be empty")

        // Step 1: AdvanceDay to generate inbox and heroes
        val result1 = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result1.state

        // Verify inbox was generated
        assertTrue(state.contracts.inbox.size >= 1, "Inbox should have contracts after day advance")
        assertTrue(state.heroes.roster.size >= 1, "Heroes should have arrived")

        // Extract events (filter InvariantViolated)
        val events1 = result1.events.filterNot { it is InvariantViolated }
        assertTrue(events1.any { it is DayStarted }, "Should have DayStarted event")
        assertTrue(events1.any { it is InboxGenerated }, "Should have InboxGenerated event")
        assertTrue(events1.any { it is HeroesArrived }, "Should have HeroesArrived event")
        assertTrue(events1.any { it is DayEnded }, "Should have DayEnded event")

        // Step 2: PostContract from inbox
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        val result2 = step(state, PostContract(inboxId = inboxId, fee = 0, cmdId = cmdId++), rng)
        state = result2.state

        // Verify contract was posted
        assertTrue(state.contracts.board.size >= 1, "Board should have posted contract")
        assertEquals(0, state.contracts.inbox.filter { it.id.value.toLong() == inboxId }.size,
            "Posted contract should be removed from inbox")

        val events2 = result2.events.filterNot { it is InvariantViolated }
        assertTrue(events2.any { it is ContractPosted }, "Should have ContractPosted event")

        // Step 3: AdvanceDay - heroes take contract AND WIP advances (starts at 2, then advances to 1)
        val result3 = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result3.state

        val events3 = result3.events.filterNot { it is InvariantViolated }
        val contractTaken = events3.filterIsInstance<ContractTaken>()
        assertTrue(contractTaken.isNotEmpty(), "At least one contract should be taken")
        assertTrue(events3.any { it is WipAdvanced }, "Should have WipAdvanced event in same day")

        // Verify active contract exists with daysRemaining=1 (taken at 2, then advanced)
        val activeContracts = state.contracts.active.filter { it.status == ActiveStatus.WIP }
        assertTrue(activeContracts.isNotEmpty(), "Should have active WIP contracts")
        val firstActive = activeContracts.first()
        assertEquals(1, firstActive.daysRemaining, "Contract taken at 2, then advanced to 1 same day")

        // Step 4: AdvanceDay - Contract resolves (daysRemaining=0)
        val result4 = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result4.state

        val events4 = result4.events.filterNot { it is InvariantViolated }
        val contractResolved = events4.filterIsInstance<ContractResolved>()
        assertTrue(contractResolved.isNotEmpty(), "Contract should be resolved")

        // Verify return was created
        val returns = state.contracts.returns.filter { it.requiresPlayerClose }
        assertTrue(returns.isNotEmpty(), "Should have returns requiring close")
        val firstReturn = returns.first()
        assertEquals(Outcome.SUCCESS, firstReturn.outcome, "PoC always returns SUCCESS")
        assertTrue(firstReturn.trophiesCount in 1..3, "SUCCESS should generate 1-3 trophies")

        // Verify active contract is RETURN_READY
        val returnReadyContracts = state.contracts.active.filter { it.status == ActiveStatus.RETURN_READY }
        assertTrue(returnReadyContracts.isNotEmpty(), "Should have RETURN_READY contracts")

        // Step 5: CloseReturn
        val activeContractId = firstReturn.activeContractId.value.toLong()
        val trophiesBeforeClose = state.economy.trophiesStock
        val trophiesInReturn = firstReturn.trophiesCount

        val result5 = step(state, CloseReturn(activeContractId = activeContractId, cmdId = cmdId++), rng)
        state = result5.state

        val events5 = result5.events.filterNot { it is InvariantViolated }
        assertTrue(events5.any { it is ReturnClosed }, "Should have ReturnClosed event")

        // Verify trophies were added to stock
        assertEquals(trophiesBeforeClose + trophiesInReturn, state.economy.trophiesStock,
            "Trophies should be added to stock based on return")
        assertTrue(state.economy.trophiesStock >= 1, "Should have trophies after close")

        // Verify return was removed
        assertEquals(0, state.contracts.returns.filter { it.activeContractId.value.toLong() == activeContractId }.size,
            "Return should be removed after close")

        // Step 6: SellTrophies (amount=0 means sell all)
        val moneyBeforeSell = state.economy.moneyCopper
        val trophiesBeforeSell = state.economy.trophiesStock
        assertTrue(trophiesBeforeSell > 0, "Should have trophies before selling")

        val result6 = step(state, SellTrophies(amount = 0, cmdId = cmdId++), rng)
        state = result6.state

        val events6 = result6.events.filterNot { it is InvariantViolated }
        val trophySold = events6.filterIsInstance<TrophySold>()

        // Verify sell worked
        assertEquals(1, trophySold.size, "Should have exactly one TrophySold event")
        val soldEvent = trophySold.first()
        assertEquals(trophiesBeforeSell, soldEvent.amount, "Should sell all trophies")
        assertEquals(trophiesBeforeSell, soldEvent.moneyGained, "Should gain 1 copper per trophy")
        assertEquals(moneyBeforeSell + trophiesBeforeSell, state.economy.moneyCopper,
            "Money should increase by trophies sold")
        assertEquals(0, state.economy.trophiesStock, "Trophies should be 0 after selling all")

        // Stability verification
        // Current PoC: requiresPlayerClose=true means no stability change
        val allStabilityEvents = (events1 + events2 + events3 + events4 + events5 + events6)
            .filterIsInstance<StabilityUpdated>()

        // Since all returns have requiresPlayerClose=true, no stability changes occur
        assertEquals(0, allStabilityEvents.size, "Current PoC should have no stability changes (requiresPlayerClose=true)")
        assertEquals(50, state.region.stability, "Stability should remain at initial value")

        // Final state hash verification (deterministic check)
        val finalHash = hashState(state)
        assertEquals(64, finalHash.length, "Hash should be 64 characters")
        assertTrue(finalHash.all { it in '0'..'9' || it in 'a'..'f' }, "Hash should be lowercase hex")
    }

    @Test
    fun `poc scenario with positive fee reduces money`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        assertEquals(100, state.economy.moneyCopper, "Initial money should be 100")

        // AdvanceDay to generate inbox
        val result1 = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result1.state

        // PostContract with fee=5
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        val result2 = step(state, PostContract(inboxId = inboxId, fee = 5, cmdId = cmdId++), rng)
        state = result2.state

        // Verify money is not deducted but reserved (escrow semantics)
        assertEquals(100, state.economy.moneyCopper, "Money should remain 100 (not deducted, only reserved)")
        assertEquals(5, state.economy.reservedCopper, "Fee should be reserved (escrowed)")
        assertEquals(95, state.economy.moneyCopper - state.economy.reservedCopper, "Available money should be 95")
    }

    @Test
    fun `poc scenario multiple contracts in sequence`() {
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Day 1: Generate inbox
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state

        val inbox1Count = state.contracts.inbox.size
        assertTrue(inbox1Count >= 2, "Should have at least 2 inbox contracts")

        // Post first contract
        val inbox1Id = state.contracts.inbox[0].id.value.toLong()
        state = step(state, PostContract(inboxId = inbox1Id, fee = 0, cmdId = cmdId++), rng).state

        // Post second contract
        val inbox2Id = state.contracts.inbox[0].id.value.toLong() // First in remaining list
        state = step(state, PostContract(inboxId = inbox2Id, fee = 0, cmdId = cmdId++), rng).state

        // Verify both are on board
        assertTrue(state.contracts.board.size >= 2, "Should have at least 2 board contracts")

        // Day 2: Contracts get taken
        state = step(state, AdvanceDay(cmdId = cmdId++), rng).state

        // Should have active contracts
        assertTrue(state.contracts.active.size >= 1, "Should have active contracts")
    }

    @Test
    fun `event seq numbering is sequential in all commands`() {
        var state = initialState(42u)
        val rng = Rng(100L)

        // Test AdvanceDay
        val result1 = step(state, AdvanceDay(cmdId = 1L), rng)
        val events1 = result1.events.filterNot { it is InvariantViolated }
        events1.forEachIndexed { index, event ->
            assertEquals((index + 1).toLong(), event.seq, "AdvanceDay: seq should be sequential")
        }

        state = result1.state

        // Test PostContract
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        val result2 = step(state, PostContract(inboxId = inboxId, fee = 0, cmdId = 2L), rng)
        val events2 = result2.events.filterNot { it is InvariantViolated }
        events2.forEachIndexed { index, event ->
            assertEquals((index + 1).toLong(), event.seq, "PostContract: seq should be sequential")
        }

        state = result2.state

        // Test SellTrophies
        val result3 = step(state, SellTrophies(amount = 0, cmdId = 3L), rng)
        val events3 = result3.events.filterNot { it is InvariantViolated }
        events3.forEachIndexed { index, event ->
            assertEquals((index + 1).toLong(), event.seq, "SellTrophies: seq should be sequential")
        }
    }

    @Test
    fun `revision increments on each accepted command`() {
        var state = initialState(42u)
        val rng = Rng(100L)

        assertEquals(0L, state.meta.revision, "Initial revision should be 0")

        state = step(state, AdvanceDay(cmdId = 1L), rng).state
        assertEquals(1L, state.meta.revision, "Revision should be 1 after first command")

        state = step(state, AdvanceDay(cmdId = 2L), rng).state
        assertEquals(2L, state.meta.revision, "Revision should be 2 after second command")

        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(state, PostContract(inboxId = inboxId, fee = 0, cmdId = 3L), rng).state
        assertEquals(3L, state.meta.revision, "Revision should be 3 after third command")
    }

    @Test
    fun `day index increments only on AdvanceDay`() {
        var state = initialState(42u)
        val rng = Rng(100L)

        assertEquals(0, state.meta.dayIndex, "Initial day should be 0")

        state = step(state, AdvanceDay(cmdId = 1L), rng).state
        assertEquals(1, state.meta.dayIndex, "Day should be 1 after AdvanceDay")

        val inboxId = state.contracts.inbox.first().id.value.toLong()
        state = step(state, PostContract(inboxId = inboxId, fee = 0, cmdId = 2L), rng).state
        assertEquals(1, state.meta.dayIndex, "Day should still be 1 after PostContract")

        state = step(state, AdvanceDay(cmdId = 3L), rng).state
        assertEquals(2, state.meta.dayIndex, "Day should be 2 after second AdvanceDay")
    }

    @Test
    fun `happy path no invariant violations with fee escrow and trophy flow`() {
        // Full flow: post -> take -> resolve -> close -> sell
        // Verify: reserved decreases on close, money decreases/increases, no invariant violations
        var state = initialState(42u)
        val rng = Rng(300L)
        var cmdId = 1L

        val initialMoney = state.economy.moneyCopper
        assertEquals(100, initialMoney)

        // Day 1: Generate inbox
        val result1 = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result1.state
        assertEquals(0, result1.events.filterIsInstance<InvariantViolated>().size, "No violations after day 1")

        // Post contract with fee=10
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        val result2 = step(state, PostContract(inboxId = inboxId, fee = 10, cmdId = cmdId++), rng)
        state = result2.state
        assertEquals(0, result2.events.filterIsInstance<InvariantViolated>().size, "No violations after post")

        // Verify escrow
        assertEquals(100, state.economy.moneyCopper, "Money unchanged")
        assertEquals(10, state.economy.reservedCopper, "Fee reserved")
        assertEquals(90, state.economy.moneyCopper - state.economy.reservedCopper, "Available is 90")

        // Day 2: Take + advance
        val result3 = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result3.state
        assertEquals(0, result3.events.filterIsInstance<InvariantViolated>().size, "No violations after take")

        // Day 3: Resolve
        val result4 = step(state, AdvanceDay(cmdId = cmdId++), rng)
        state = result4.state
        assertEquals(0, result4.events.filterIsInstance<InvariantViolated>().size, "No violations after resolve")

        // Verify return created with trophies
        val returns = state.contracts.returns.filter { it.requiresPlayerClose }
        assertTrue(returns.isNotEmpty())
        val returnPacket = returns.first()
        assertTrue(returnPacket.trophiesCount > 0, "Should have trophies")
        assertEquals(0, state.economy.trophiesStock, "Trophies not deposited yet")

        // Close return
        val activeId = returnPacket.activeContractId.value.toLong()
        val moneyBeforeClose = state.economy.moneyCopper
        val reservedBeforeClose = state.economy.reservedCopper

        val result5 = step(state, CloseReturn(activeContractId = activeId, cmdId = cmdId++), rng)
        state = result5.state
        assertEquals(0, result5.events.filterIsInstance<InvariantViolated>().size, "No violations after close")

        // Verify fee payout
        assertEquals(moneyBeforeClose - 10, state.economy.moneyCopper, "Money decreased by fee")
        assertEquals(reservedBeforeClose - 10, state.economy.reservedCopper, "Reserved decreased by fee")

        // Verify trophies deposited
        assertEquals(returnPacket.trophiesCount, state.economy.trophiesStock, "Trophies deposited")

        // Verify board unlocked
        val board = state.contracts.board.first()
        assertEquals(BoardStatus.COMPLETED, board.status, "Board should be COMPLETED")

        // Sell trophies
        val moneyBeforeSell = state.economy.moneyCopper
        val trophiesBeforeSell = state.economy.trophiesStock

        val result6 = step(state, SellTrophies(amount = 0, cmdId = cmdId++), rng)
        state = result6.state
        assertEquals(0, result6.events.filterIsInstance<InvariantViolated>().size, "No violations after sell")

        // Verify sell
        assertEquals(0, state.economy.trophiesStock, "Trophies sold")
        assertEquals(moneyBeforeSell + trophiesBeforeSell, state.economy.moneyCopper, "Money increased by trophies")

        // Final money check: started with 100, paid out 10 fee, gained N trophies
        assertEquals(initialMoney - 10 + returnPacket.trophiesCount, state.economy.moneyCopper)

        // Final invariants check - no violations on happy path
        val finalViolations = verifyInvariants(state)
        assertEquals(0, finalViolations.size, "No invariant violations on happy path")
    }
}
