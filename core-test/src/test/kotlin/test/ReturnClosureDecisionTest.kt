package test

// TEST LEVEL: P1 — Critical unit tests for accept/reject return closure (ADR-004)

import core.*
import core.primitives.*
import core.state.*
import test.helpers.*
import kotlin.test.*

/**
 * P1 CRITICAL: Return Closure Decision Tests (ADR-004)
 *
 * Tests explicit accept/reject decisions for manual return closure to prevent
 * STRICT policy deadlocks.
 *
 * Coverage:
 * - A) Validation / Policy (tests 1-3)
 * - B) State Transitions / Lifecycle (tests 4-5)
 * - C) Economy / Escrow (tests 6-8)
 * - D) Progression / Counters (tests 9-10)
 * - E) Events / Sequencing (tests 11-13)
 * - F) Determinism Contract (tests 14-15)
 */
@P1
@Smoke
class ReturnClosureDecisionTest {

    // ═════════════════════════════════════════════════════════════════════════
    // A) Validation / Policy
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `STRICT requires explicit decision - null decision rejected`() {
        // GIVEN: STRICT policy + return requiring player close
        val fixture = setupReturnRequiringClose(
            proofPolicy = ProofPolicy.STRICT,
            moneyCopper = 100,
            fee = 50
        )

        // WHEN: CloseReturn with decision=null
        val cmd = CloseReturn(activeContractId = 1L, decision = null, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: command rejected with strict_policy_requires_decision
        assertSingleRejection(result.events, RejectReason.INVALID_ARG)
        val rejection = result.events.filterIsInstance<CommandRejected>().first()
        assertTrue(rejection.detail.contains("STRICT"), "Detail should mention STRICT policy")
        assertTrue(rejection.detail.contains("decision"), "Detail should mention decision requirement")
    }

    @Test
    fun `REJECT bypasses fee money validation - succeeds even with insufficient funds`() {
        // GIVEN: return requiring close, moneyCopper < fee
        val fixture = setupReturnRequiringClose(
            proofPolicy = ProofPolicy.FAST,
            moneyCopper = 10,  // Less than fee
            fee = 100
        )

        // WHEN: CloseReturn with REJECT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.REJECT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: command accepted; lifecycle terminates; no payment attempted
        assertNoRejections(result.events, "REJECT should bypass money validation")
        assertEventCount<ReturnRejected>(result.events, 1)
        assertEquals(10, result.state.economy.moneyCopper, "Money should be unchanged (no fee payment)")
    }

    @Test
    fun `ACCEPT retains money validation - rejects with insufficient funds`() {
        // GIVEN: return requiring close, moneyCopper < fee
        val fixture = setupReturnRequiringClose(
            proofPolicy = ProofPolicy.FAST,
            moneyCopper = 10,  // Less than fee
            fee = 100
        )

        // WHEN: CloseReturn with ACCEPT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.ACCEPT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: command rejected due to insufficient funds
        assertSingleRejection(result.events, RejectReason.INVALID_STATE)
        val rejection = result.events.filterIsInstance<CommandRejected>().first()
        assertTrue(rejection.detail.contains("fund", ignoreCase = true), "Detail should mention funds")
    }

    // ═════════════════════════════════════════════════════════════════════════
    // B) State Transitions / Lifecycle Termination
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `REJECT terminates lifecycle - return removed, active closed, hero available`() {
        // GIVEN: return requiring close
        val fixture = setupReturnRequiringClose(moneyCopper = 100, fee = 50)

        // WHEN: close with REJECT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.REJECT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: lifecycle terminated
        assertLifecycleTerminated(result.state, heroId = 1L, activeId = 1L)
    }

    @Test
    fun `ACCEPT terminates lifecycle - same as legacy behavior`() {
        // GIVEN: return requiring close
        val fixture = setupReturnRequiringClose(moneyCopper = 100, fee = 50)

        // WHEN: close with ACCEPT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.ACCEPT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: lifecycle terminated (baseline parity)
        assertLifecycleTerminated(result.state, heroId = 1L, activeId = 1L)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // C) Economy / Escrow
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `REJECT releases escrow and reserved funds - no stuck reserved`() {
        // GIVEN: return with clientDeposit held as reserved
        val fixture = setupReturnRequiringClose(
            moneyCopper = 100,
            reservedCopper = 20,  // clientDeposit
            fee = 50,
            clientDeposit = 20
        )

        val initialReserved = fixture.initial.economy.reservedCopper

        // WHEN: close with REJECT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.REJECT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: reserved decreases by clientDeposit amount
        val expectedReserved = initialReserved - 20
        assertEquals(expectedReserved, result.state.economy.reservedCopper,
            "Reserved should decrease by clientDeposit (escrow released)")
        assertTrue(result.state.economy.reservedCopper >= 0, "Reserved must never be negative")
    }

    @Test
    fun `REJECT does not pay fee - money unchanged by fee`() {
        // GIVEN: return requiring close
        val fixture = setupReturnRequiringClose(moneyCopper = 100, fee = 50)
        val initialMoney = fixture.initial.economy.moneyCopper

        // WHEN: close with REJECT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.REJECT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: moneyCopper unchanged (no fee payment)
        assertEquals(initialMoney, result.state.economy.moneyCopper,
            "Money should be unchanged - no fee payment on REJECT")
    }

    @Test
    fun `REJECT awards 0 trophies - trophiesStock unchanged`() {
        // GIVEN: return with trophies, salvage=GUILD
        val fixture = setupReturnRequiringClose(
            trophiesStock = 10,
            trophiesCount = 5,
            salvage = SalvagePolicy.GUILD
        )
        val initialTrophies = fixture.initial.economy.trophiesStock

        // WHEN: close with REJECT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.REJECT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: trophiesStock unchanged (0 trophies awarded)
        assertEquals(initialTrophies, result.state.economy.trophiesStock,
            "Trophies should be unchanged - 0 awarded on REJECT")
    }

    // ═════════════════════════════════════════════════════════════════════════
    // D) Progression / Counters
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `REJECT does not increment completion counters - FAIL-like behavior`() {
        // GIVEN: return requiring close
        val fixture = setupReturnRequiringClose()
        val initialCompleted = fixture.initial.guild.completedContractsTotal

        // WHEN: close with REJECT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.REJECT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: completedContractsTotal unchanged (FAIL-like)
        assertEquals(initialCompleted, result.state.guild.completedContractsTotal,
            "Completion counter should NOT increment on REJECT")
    }

    @Test
    fun `ACCEPT preserves existing progression behavior - increments counter`() {
        // GIVEN: return requiring close
        val fixture = setupReturnRequiringClose(moneyCopper = 100, fee = 50)
        val initialCompleted = fixture.initial.guild.completedContractsTotal

        // WHEN: close with ACCEPT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.ACCEPT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: completedContractsTotal incremented (existing behavior)
        assertEquals(initialCompleted + 1, result.state.guild.completedContractsTotal,
            "Completion counter should increment on ACCEPT")
    }

    // ═════════════════════════════════════════════════════════════════════════
    // E) Events / Sequencing / Serialization
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `REJECT emits ReturnRejected event - not ReturnClosed`() {
        // GIVEN: return requiring close
        val fixture = setupReturnRequiringClose()

        // WHEN: close with REJECT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.REJECT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: ReturnRejected emitted, seq assigned correctly
        assertEventCount<ReturnRejected>(result.events, 1, "Should emit exactly one ReturnRejected")
        assertEventCount<ReturnClosed>(result.events, 0, "Should NOT emit ReturnClosed")

        val rejected = result.events.filterIsInstance<ReturnRejected>().first()
        assertEquals(1, rejected.activeContractId, "Event should reference correct activeContractId")
        assertSequentialSeq(result.events, "REJECT")
    }

    @Test
    fun `ACCEPT emits ReturnClosed event - not ReturnRejected`() {
        // GIVEN: return requiring close
        val fixture = setupReturnRequiringClose(moneyCopper = 100, fee = 50)

        // WHEN: close with ACCEPT
        val cmd = CloseReturn(activeContractId = 1L, decision = ReturnDecision.ACCEPT, cmdId = 1L)
        val result = step(fixture.initial, cmd, fixture.rng)

        // THEN: ReturnClosed emitted (existing behavior)
        assertEventCount<ReturnClosed>(result.events, 1, "Should emit exactly one ReturnClosed")
        assertEventCount<ReturnRejected>(result.events, 0, "Should NOT emit ReturnRejected")
        assertSequentialSeq(result.events, "ACCEPT")
    }

    @Test
    fun `ReturnRejected serializes to canonical JSON`() {
        // GIVEN: ReturnRejected event
        val event = ReturnRejected(
            day = 1,
            revision = 5L,
            cmdId = 10L,
            seq = 1L,
            activeContractId = 42
        )

        // WHEN: serialize to JSON
        val json = core.serde.serializeEvents(listOf(event))

        // THEN: JSON contains expected fields
        assertTrue(json.contains("\"type\":\"ReturnRejected\""), "JSON should have type field")
        assertTrue(json.contains("\"activeContractId\":42"), "JSON should have activeContractId")
        assertTrue(json.contains("\"day\":1"), "JSON should have day")
        assertTrue(json.contains("\"seq\":1"), "JSON should have seq")
    }

    // ═════════════════════════════════════════════════════════════════════════
    // F) Determinism Contract
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `No RNG draws on close - ACCEPT and REJECT both deterministic`() {
        // GIVEN: return requiring close
        val fixture = setupReturnRequiringClose(moneyCopper = 100, fee = 50)

        // WHEN: close with REJECT
        val rng1 = core.rng.Rng(100L)
        val drawsBefore1 = rng1.draws
        step(fixture.initial, CloseReturn(1L, ReturnDecision.REJECT, 1L), rng1)
        val drawsAfter1 = rng1.draws

        // WHEN: close with ACCEPT
        val rng2 = core.rng.Rng(100L)
        val drawsBefore2 = rng2.draws
        step(fixture.initial, CloseReturn(1L, ReturnDecision.ACCEPT, 2L), rng2)
        val drawsAfter2 = rng2.draws

        // THEN: no RNG draws in either case
        assertEquals(drawsBefore1, drawsAfter1, "REJECT should not perform any RNG draws")
        assertEquals(drawsBefore2, drawsAfter2, "ACCEPT should not perform any RNG draws")
        assertEquals(0L, drawsAfter1, "Total draws should be 0 for REJECT")
        assertEquals(0L, drawsAfter2, "Total draws should be 0 for ACCEPT")
    }

    @Test
    fun `Replay stability - same seed and decisions produce identical results`() {
        // GIVEN: scenario with close decisions
        val scenario = Scenario(
            scenarioId = "close_decision_determinism",
            stateSeed = 42u,
            rngSeed = 100L,
            commands = listOf(
                CloseReturn(1L, ReturnDecision.REJECT, 1L),
                CloseReturn(2L, ReturnDecision.ACCEPT, 2L)
            )
        )

        // Setup state with two returns
        val state = setupMultipleReturns()
        val rng = core.rng.Rng(100L)

        // Run twice
        val result1 = step(state, scenario.commands[0], core.rng.Rng(100L))
        val result2 = step(state, scenario.commands[0], core.rng.Rng(100L))

        // THEN: identical state and event hashes
        val hash1 = core.hash.hashState(result1.state)
        val hash2 = core.hash.hashState(result2.state)
        assertEquals(hash1, hash2, "State hashes must match for same inputs")

        val eventHash1 = core.hash.hashEvents(result1.events)
        val eventHash2 = core.hash.hashEvents(result2.events)
        assertEquals(eventHash1, eventHash2, "Event hashes must match for same inputs")
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Additional Policy Tests
    // ═════════════════════════════════════════════════════════════════════════

    @Test
    fun `STRICT with damaged proof - ACCEPT denied, REJECT allowed`() {
        // GIVEN: STRICT policy + damaged proof
        val fixture = setupReturnRequiringClose(
            proofPolicy = ProofPolicy.STRICT,
            trophiesQuality = Quality.DAMAGED,
            moneyCopper = 100,
            fee = 50
        )

        // WHEN: try ACCEPT
        val acceptCmd = CloseReturn(1L, ReturnDecision.ACCEPT, 1L)
        val acceptResult = step(fixture.initial, acceptCmd, core.rng.Rng(100L))

        // THEN: ACCEPT blocked by policy
        assertEventCount<ReturnClosureBlocked>(acceptResult.events, 1)
        val blocked = acceptResult.events.filterIsInstance<ReturnClosureBlocked>().first()
        assertEquals("strict_policy_damaged_proof", blocked.reason)

        // WHEN: try REJECT
        val rejectCmd = CloseReturn(1L, ReturnDecision.REJECT, 2L)
        val rejectResult = step(fixture.initial, rejectCmd, core.rng.Rng(100L))

        // THEN: REJECT succeeds (unblocks gameplay)
        assertEventCount<ReturnRejected>(rejectResult.events, 1)
        assertNoRejections(rejectResult.events)
    }

    @Test
    fun `STRICT with suspected theft - ACCEPT denied, REJECT allowed`() {
        // GIVEN: STRICT policy + suspected theft
        val fixture = setupReturnRequiringClose(
            proofPolicy = ProofPolicy.STRICT,
            suspectedTheft = true,
            moneyCopper = 100,
            fee = 50
        )

        // WHEN: try ACCEPT
        val acceptCmd = CloseReturn(1L, ReturnDecision.ACCEPT, 1L)
        val acceptResult = step(fixture.initial, acceptCmd, core.rng.Rng(100L))

        // THEN: ACCEPT blocked by policy
        assertEventCount<ReturnClosureBlocked>(acceptResult.events, 1)
        val blocked = acceptResult.events.filterIsInstance<ReturnClosureBlocked>().first()
        assertEquals("strict_policy_theft_suspected", blocked.reason)

        // WHEN: try REJECT
        val rejectCmd = CloseReturn(1L, ReturnDecision.REJECT, 2L)
        val rejectResult = step(fixture.initial, rejectCmd, core.rng.Rng(100L))

        // THEN: REJECT succeeds (unblocks gameplay)
        assertEventCount<ReturnRejected>(rejectResult.events, 1)
        assertNoRejections(rejectResult.events)
    }

    // ═════════════════════════════════════════════════════════════════════════
    // Test Fixtures & Helpers
    // ═════════════════════════════════════════════════════════════════════════

    private fun setupReturnRequiringClose(
        proofPolicy: ProofPolicy = ProofPolicy.FAST,
        moneyCopper: Int = 100,
        reservedCopper: Int = 0,
        trophiesStock: Int = 0,
        fee: Int = 50,
        clientDeposit: Int = 0,
        trophiesCount: Int = 3,
        trophiesQuality: Quality = Quality.OK,
        salvage: SalvagePolicy = SalvagePolicy.GUILD,
        suspectedTheft: Boolean = false
    ): LockedBoardFixture {
        return lockedBoardFixture(
            seed = 42u,
            rngSeed = 100L,
            boardStatus = BoardStatus.LOCKED,
            economy = EconomyState(
                moneyCopper = moneyCopper,
                reservedCopper = reservedCopper,
                trophiesStock = trophiesStock
            ),
            actives = listOf(
                active(
                    id = 1L,
                    heroIds = listOf(1L),
                    status = ActiveStatus.RETURN_READY,
                    boardContractId = 1L
                )
            ),
            returns = listOf(
                returnPacket(
                    activeId = 1L,
                    heroIds = listOf(1L),
                    boardContractId = 1L,
                    outcome = Outcome.PARTIAL,
                    trophiesCount = trophiesCount,
                    trophiesQuality = trophiesQuality,
                    requiresPlayerClose = true,
                    suspectedTheft = suspectedTheft
                )
            ),
            heroes = listOf(
                hero(
                    id = 1L,
                    status = HeroStatus.ON_MISSION
                )
            )
        ).let { fixture ->
            // Update board contract with fee and salvage
            fixture.copy(
                initial = fixture.initial.copy(
                    contracts = fixture.initial.contracts.copy(
                        board = listOf(
                            boardContract(
                                id = 1L,
                                status = BoardStatus.LOCKED,
                                fee = fee,
                                salvage = salvage,
                                clientDeposit = clientDeposit
                            )
                        )
                    ),
                    guild = fixture.initial.guild.copy(
                        proofPolicy = proofPolicy
                    )
                )
            )
        }
    }

    private fun setupMultipleReturns(): GameState {
        return lockedBoardState(
            seed = 42u,
            economy = EconomyState(moneyCopper = 200, reservedCopper = 0, trophiesStock = 0),
            actives = listOf(
                active(1L, listOf(1L), ActiveStatus.RETURN_READY),
                active(2L, listOf(2L), ActiveStatus.RETURN_READY)
            ),
            returns = listOf(
                returnPacket(1L, listOf(1L)),
                returnPacket(2L, listOf(2L))
            ),
            heroes = listOf(
                hero(1L, status = HeroStatus.ON_MISSION),
                hero(2L, status = HeroStatus.ON_MISSION)
            )
        )
    }

    private fun assertLifecycleTerminated(state: GameState, heroId: Long, activeId: Long) {
        // Return removed
        val returnPresent = state.contracts.returns.any { it.activeContractId.value.toLong() == activeId }
        assertFalse(returnPresent, "Return packet should be removed")

        // Active contract closed
        val activeContract = state.contracts.active.firstOrNull { it.id.value.toLong() == activeId }
        if (activeContract != null) {
            assertEquals(ActiveStatus.CLOSED, activeContract.status, "Active contract should be CLOSED")
        }

        // Hero available
        val heroStatus = state.heroes.roster.first { it.id.value.toLong() == heroId }.status
        assertEquals(HeroStatus.AVAILABLE, heroStatus, "Hero should be AVAILABLE")
    }
}
