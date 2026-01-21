// FILE: core-test/src/test/kotlin/test/P1_021_CreateContractTest.kt
package test

import core.*
import core.primitives.Rank
import core.primitives.SalvagePolicy
import core.state.initialState
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import core.rng.Rng

/**
 * P1_021: CreateContract Command Tests
 *
 * Validates R1 lifecycle command: CreateContract
 * Tests contract draft creation in inbox with validation rules
 */
class P1_021_CreateContractTest {

    @Test
    fun `CreateContract creates draft in inbox`() {
        var state = initialState(123u)
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
        assertTrue(draft != null, "Draft should exist in inbox")
        assertEquals("Goblin Raid", draft.title)

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

        val (_, events) = step(state, cmd, rng)

        // Should emit Rejected
        assertEquals(1, events.size)
        val event = events[0] as Rejected
        assertTrue(event.detail.contains("blank") || event.detail.contains("empty"),
            "Rejection reason should mention blank/empty title")
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

        val (_, events) = step(state, cmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as Rejected
        assertTrue(event.detail.contains("difficulty"),
            "Rejection reason should mention difficulty")
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

        val (_, events) = step(state, cmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as Rejected
        assertTrue(event.detail.contains("difficulty"),
            "Rejection reason should mention difficulty")
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

        val (_, events) = step(state, cmd, rng)

        assertEquals(1, events.size)
        val event = events[0] as Rejected
        assertTrue(event.detail.contains("reward"),
            "Rejection reason should mention reward")
    }

    @Test
    fun `CreateContract ID monotonicity`() {
        var state = initialState(123u)
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

        // All drafts should be in inbox
        assertEquals(ranks.size, state.inbox.size)
    }

    @Test
    fun `CreateContract deterministic with same seed`() {
        val state = initialState(123u)
        val rng1 = Rng(456L)
        val rng2 = Rng(456L)

        val cmd = CreateContract(
            title = "Test Contract",
            rank = Rank.F,
            difficulty = 30,
            reward = 50,
            salvage = SalvagePolicy.GUILD,
            cmdId = 1L
        )

        val (state1, events1) = step(state, cmd, rng1)
        val (state2, events2) = step(state, cmd, rng2)

        assertEquals(events1, events2, "Events should be identical with same seed")
        assertEquals(state1, state2, "States should be identical with same seed")
    }
}
