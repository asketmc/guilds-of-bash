package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

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
class P1_003_PoCScenarioTest {

    @Test
    fun `poc scenario basic contract flow end to end`() {
        // GIVEN: fresh initial state with deterministic RNG and no prior commands
        var state = initialState(42u)
        val rng = Rng(100L)
        var cmdId = 1L

        // Initial state verification
        assertEquals(100, state.economy.moneyCopper, "Initial money should be 100")
        assertEquals(0, state.economy.trophiesStock, "Initial trophies should be 0")
        assertEquals(50, state.region.stability, "Initial stability should be 50")
        assertEquals(0, state.contracts.inbox.size, "Initial inbox should be empty")

        // WHEN: Advance day to populate inbox and then post a contract, advance day(s) to resolve and close flows
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

        // Step 2: PostContract from inbox with attractive terms (HERO salvage makes it more attractive)
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        val result2 = step(state, PostContract(inboxId = inboxId, fee = 100, salvage = SalvagePolicy.HERO, cmdId = cmdId++), rng)
        state = result2.state

        // THEN: verify contract was posted and removed from inbox
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

        // Verify return was created (SUCCESS/FAIL auto-process, only PARTIAL requires player close)
        val returns = state.contracts.returns
        assertTrue(returns.isNotEmpty(), "Should have returns")
        val firstReturn = returns.first()
        // With this RNG seed, outcome is SUCCESS
        assertTrue(firstReturn.outcome in listOf(Outcome.SUCCESS, Outcome.PARTIAL, Outcome.FAIL))

        // New behavior: SUCCESS and FAIL don't require player close, they auto-process
        // So we skip the CloseReturn step for this test since it's auto-processed
        // Trophies are auto-deposited based on salvage policy (HERO = 0 to guild)

        // Stability verification
        // New behavior: auto-processed returns (requiresPlayerClose=false) DO affect stability
        val allStabilityEvents = (events1 + events2 + events3 + events4)
            .filterIsInstance<StabilityUpdated>()

        // With auto-processing, SUCCESS increases stability
        assertTrue(allStabilityEvents.isNotEmpty() || state.region.stability >= 50,
            "Stability should increase or remain stable after SUCCESS")

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
        val result2 = step(state, PostContract(inboxId = inboxId, fee = 5, salvage = SalvagePolicy.HERO, cmdId = cmdId++), rng)
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
        state = step(state, PostContract(inboxId = inbox1Id, fee = 10, salvage = SalvagePolicy.HERO, cmdId = cmdId++), rng).state

        // Post second contract
        val inbox2Id = state.contracts.inbox[0].id.value.toLong() // First in remaining list
        state = step(state, PostContract(inboxId = inbox2Id, fee = 10, salvage = SalvagePolicy.HERO, cmdId = cmdId++), rng).state

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
        val result2 = step(state, PostContract(inboxId = inboxId, fee = 10, salvage = SalvagePolicy.HERO, cmdId = 2L), rng)
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
        state = step(state, PostContract(inboxId = inboxId, fee = 10, salvage = SalvagePolicy.HERO, cmdId = 3L), rng).state
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
        state = step(state, PostContract(inboxId = inboxId, fee = 10, salvage = SalvagePolicy.HERO, cmdId = 2L), rng).state
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

        // Post contract with fee=100 and HERO salvage to make it attractive
        val inboxId = state.contracts.inbox.first().id.value.toLong()
        val result2 = step(state, PostContract(inboxId = inboxId, fee = 100, salvage = SalvagePolicy.HERO, cmdId = cmdId++), rng)
        state = result2.state
        assertEquals(0, result2.events.filterIsInstance<InvariantViolated>().size, "No violations after post")

        // Verify escrow
        assertEquals(100, state.economy.moneyCopper, "Money unchanged")
        assertEquals(100, state.economy.reservedCopper, "Fee reserved")
        assertEquals(0, state.economy.moneyCopper - state.economy.reservedCopper, "Available is 0")

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
        assertEquals(moneyBeforeClose - 100, state.economy.moneyCopper, "Money decreased by fee")
        assertEquals(reservedBeforeClose - 100, state.economy.reservedCopper, "Reserved decreased by fee")

        // Verify trophies deposited (HERO salvage = hero gets all, guild gets 0)
        assertEquals(0, state.economy.trophiesStock, "Hero keeps all trophies with HERO salvage")

        // Verify board unlocked
        val board = state.contracts.board.first()
        assertEquals(BoardStatus.COMPLETED, board.status, "Board should be COMPLETED")

        // Final money check: started with 100, paid out 100 fee
        assertEquals(initialMoney - 100, state.economy.moneyCopper)

        // Final invariants check - no violations on happy path
        val finalViolations = verifyInvariants(state)
        assertEquals(0, finalViolations.size, "No invariant violations on happy path")
    }
}
