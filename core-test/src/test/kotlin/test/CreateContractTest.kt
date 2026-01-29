package test

import core.*
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.rng.Rng
import core.state.initialState
import org.junit.jupiter.api.Test
import test.helpers.*
import kotlin.test.*

/**
 * P2: CreateContract Command Tests
 *
 * Validates R1 lifecycle command: CreateContract
 * Tests contract draft creation in inbox with validation rules
 *
 * Notes:
 * - initialState(...) seeds the inbox with initial drafts. Tests must not assume empty inbox unless
 *   they explicitly clear it.
 */
@P2
class CreateContractTest {

    @Test
    fun `CreateContract creates draft in inbox`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cmd = CreateContract(
            title = "Goblin Raid",
            rank = Rank.F,
            difficulty = 30,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        val (newState, events) = step(state, cmd, rng)

        // Should emit ContractDraftCreated
        assertEquals(1, events.size)
        val event = events[0] as ContractDraftCreated
        assertEquals("Goblin Raid", event.title)
        assertEquals(Rank.F, event.rank)
        assertEquals(30, event.difficulty)
        assertEquals(50, event.reward)
        assertEquals(SalvagePolicy.GUILD, event.salvage)

        // Draft should be in inbox
        val draft = newState.inbox.find { it.id.value == event.draftId }
        assertNotNull(draft, "Draft should exist in inbox")
        val nonNullDraft = draft!!
        assertEquals("Goblin Raid", nonNullDraft.title)

        // nextContractId should increment
        assertTrue(newState.nextContractId > state.nextContractId)
    }

    @Test
    fun `CreateContract rejects blank title`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cmd = CreateContract(
            title = "",
            rank = Rank.F,
            difficulty = 30,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        val (newState, events) = step(state, cmd, rng)

        // State must be unchanged on rejection
        assertEquals(state, newState, "State should be unchanged after rejected CreateContract")

        // Use shared helper to assert single structured rejection
        assertSingleRejection(events, RejectReason.INVALID_ARG, "Blank title should be rejected")
    }

    @Test
    fun `CreateContract rejects invalid difficulty below 0`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cmd = CreateContract(
            title = "Test Contract",
            rank = Rank.F,
            difficulty = -1,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        val (newState, events) = step(state, cmd, rng)
        assertEquals(state, newState, "State should be unchanged after rejected CreateContract")
        assertSingleRejection(events, RejectReason.INVALID_ARG, "Difficulty below 0 should be rejected")
    }

    @Test
    fun `CreateContract rejects invalid difficulty above 100`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cmd = CreateContract(
            title = "Test Contract",
            rank = Rank.F,
            difficulty = 101,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        val (newState, events) = step(state, cmd, rng)
        assertEquals(state, newState, "State should be unchanged after rejected CreateContract")
        assertSingleRejection(events, RejectReason.INVALID_ARG, "Difficulty above 100 should be rejected")
    }

    @Test
    fun `CreateContract rejects negative reward`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cmd = CreateContract(
            title = "Test Contract",
            rank = Rank.F,
            difficulty = 30,
            reward = -1,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        val (newState, events) = step(state, cmd, rng)
        assertEquals(state, newState, "State should be unchanged after rejected CreateContract")
        assertSingleRejection(events, RejectReason.INVALID_ARG, "Negative reward should be rejected")
    }

    @Test
    fun `CreateContract ID monotonicity`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cmd1 = CreateContract(
            title = "Contract 1",
            rank = Rank.F,
            difficulty = 30,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        val (state2, events1) = step(state, cmd1, rng)
        val event1 = events1[0] as ContractDraftCreated
        val id1 = event1.draftId

        val cmd2 = CreateContract(
            title = "Contract 2",
            rank = Rank.E,
            difficulty = 40,
            reward = 100,
            salvage = SalvagePolicy.HERO,
            cmdId = 2L
        )

        val (_, events2) = step(state2, cmd2, rng)
        val event2 = events2[0] as ContractDraftCreated
        val id2 = event2.draftId

        assertTrue(id2 > id1, "Contract IDs should be monotonically increasing")
    }

    @Test
    fun `CreateContract works with all ranks and salvage policies`() {
        var state = initialState(123u)
        val rng = Rng(456L)

        val ranks = listOf(Rank.F, Rank.E, Rank.D, Rank.C, Rank.B, Rank.A, Rank.S)
        val policies = listOf(SalvagePolicy.GUILD, SalvagePolicy.HERO, SalvagePolicy.SPLIT)

        val inboxSizeBefore = state.inbox.size

        for ((idx, rank) in ranks.withIndex()) {
            val policy = policies[idx % policies.size]
            val cmd = CreateContract(
                title = "Contract ${rank.name}",
                rank = rank,
                difficulty = 50,
                reward = 100,
                salvage = policy,
                cmdId = (idx + 1).toLong()
            )

            val (newState, events) = step(state, cmd, rng)
            state = newState

            assertEquals(1, events.size)
            val event = events[0] as ContractDraftCreated
            assertEquals(rank, event.rank)
            assertEquals(policy, event.salvage)
        }

        // All newly-created drafts should be appended to inbox (initial inbox may be non-empty).
        assertEquals(inboxSizeBefore + ranks.size, state.inbox.size)
    }

    /**
     * Verifies deterministic contract creation given same seed
     */
    @Test
    fun `CreateContract deterministic with same seed`() {
        val state = initialState(123u)
        val cmd = CreateContract(
            title = "Test Contract",
            rank = Rank.F,
            difficulty = 30,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        // Use existing determinism helper — provides strong hashing-based guarantees
        assertStepDeterministic(state, cmd, rngSeed = 456L, message = "CreateContract should be deterministic with same RNG seed")
    }

    @Test
    fun `CreateContract accepts difficulty boundaries 0 and 100`() {
        var state = initialState(123u)
        val rng = Rng(777L)

        val difficulties = listOf(0, 100)
        val beforeInbox = state.inbox.size

        for ((idx, diff) in difficulties.withIndex()) {
            val cmd = CreateContract(
                title = "Bound $diff",
                rank = Rank.F,
                difficulty = diff,
                reward = 10,
                salvage = SalvagePolicy.GUILD,
                cmdId = (idx + 1).toLong()
            )

            val (newState, events) = step(state, cmd, rng)
            assertEquals(1, events.size)
            val ev = events[0] as ContractDraftCreated
            assertEquals(diff, ev.difficulty)
            state = newState
        }

        assertEquals(beforeInbox + difficulties.size, state.inbox.size)
    }

    @Test
    fun `CreateContract accepts zero reward`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cmd = CreateContract(
            title = "Free Contract",
            rank = Rank.F,
            difficulty = 10,
            reward = 0,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        val (newState, events) = step(state, cmd, rng)
        assertEquals(1, events.size)
        val ev = events[0] as ContractDraftCreated
        assertEquals(0, ev.reward)
        val draft = newState.inbox.find { it.id.value == ev.draftId }
        assertNotNull(draft, "Draft should exist in inbox")
        val nonNullDraft = draft!!
        assertEquals(0, nonNullDraft.feeOffered)
        // Use shared assertion that the step produced no rejections or invariant violations
        assertStepOk(events, "Free Contract creation")
    }

    @Test
    fun `CreateContract deterministic across multiple sequential commands with same seed`() {
        val cmd1 = CreateContract(title = "Seq1", rank = Rank.F, difficulty = 10, reward = 20, salvage = SalvagePolicy.GUILD, cmdId = 1L)
        val cmd2 = CreateContract(title = "Seq2", rank = Rank.E, difficulty = 15, reward = 25, salvage = SalvagePolicy.HERO, cmdId = 2L)

        // Use the Scenario-based replay determinism helper to validate multi-step determinism
        val scenario = Scenario(
            scenarioId = "seq-create",
            stateSeed = 123u,
            rngSeed = 999L,
            commands = listOf(cmd1, cmd2)
        )
        assertReplayDeterminism(scenario, "Sequential CreateContract commands must be deterministic with same seed")
    }

    @Test
    fun `CreateContract rejects non-positive cmdId`() {
        val state = initialState(123u)
        val rng = Rng(456L)

        val cmd = CreateContract(
            title = "CmdId Zero",
            rank = Rank.F,
            difficulty = 10,
            reward = 5,
            salvage = SalvagePolicy.GUILD,
            cmdId = 0L
        )

        val (newState, events) = step(state, cmd, rng)

        // State should be unchanged
        assertEquals(state, newState)

        // Shared helper for rejecting assertions
        assertSingleRejection(events, RejectReason.INVALID_ARG, "Non-positive cmdId should be rejected")
    }
}
