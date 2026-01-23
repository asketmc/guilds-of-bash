package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.*
import core.rng.Rng
import core.state.EconomyState
import core.state.initialState
import kotlin.test.*

/**
 * P2: SellTrophies command tests.
 * Important feature-level tests for trophy selling.
 */
@P2
class P1_SellTrophiesTest {

    @Test
    fun `sell all sells all trophies when amount is 0`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                trophiesStock = 5,
                reservedCopper = 0
            )
        )
        val rng = Rng(100L)
        val cmd = SellTrophies(amount = 0, cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(0, result.state.economy.trophiesStock, "Should sell all trophies")
        assertEquals(105, result.state.economy.moneyCopper, "Should gain 5 copper (1 per trophy)")
        assertEquals(1, result.events.size, "Should emit exactly one event")

        val event = result.events[0]
        assertTrue(event is TrophySold, "Event should be TrophySold")
        event as TrophySold
        assertEquals(5, event.amount, "Should sell 5 trophies")
        assertEquals(5, event.moneyGained, "Should gain 5 copper")
        assertEquals(1L, event.seq, "Event should have seq=1")
    }

    @Test
    fun `sell partial sells exact amount`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 0,
                trophiesStock = 10,
                reservedCopper = 0
            )
        )
        val rng = Rng(100L)
        val cmd = SellTrophies(amount = 3, cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(7, result.state.economy.trophiesStock, "Should have 7 trophies left")
        assertEquals(3, result.state.economy.moneyCopper, "Should gain 3 copper")
        assertEquals(1, result.events.size, "Should emit exactly one event")

        val event = result.events[0]
        assertTrue(event is TrophySold, "Event should be TrophySold")
        event as TrophySold
        assertEquals(3, event.amount, "Should sell 3 trophies")
        assertEquals(3, event.moneyGained, "Should gain 3 copper")
    }

    @Test
    fun `sell amount greater than stock is rejected by validation`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 0,
                trophiesStock = 2,
                reservedCopper = 0
            )
        )
        val cmd = SellTrophies(amount = 3, cmdId = 1L)

        val result = canApply(state, cmd)

        assertTrue(result is ValidationResult.Rejected, "Should reject command")
        assertEquals(RejectReason.INVALID_STATE, (result as ValidationResult.Rejected).reason)
        assertTrue(result.detail.contains("Insufficient trophies"), "Detail should mention insufficient trophies")
    }

    @Test
    fun `sell with negative amount is allowed and sells all`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 50,
                trophiesStock = 8,
                reservedCopper = 0
            )
        )
        val rng = Rng(100L)
        val cmd = SellTrophies(amount = -1, cmdId = 1L)

        val validationResult = canApply(state, cmd)
        assertEquals(ValidationResult.Valid, validationResult, "Negative amount should be valid")

        val result = step(state, cmd, rng)

        assertEquals(0, result.state.economy.trophiesStock, "Should sell all trophies")
        assertEquals(58, result.state.economy.moneyCopper, "Should gain 8 copper")
    }

    @Test
    fun `sell when stock is zero emits no events`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                trophiesStock = 0,
                reservedCopper = 0
            )
        )
        val rng = Rng(100L)
        val cmd = SellTrophies(amount = 0, cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(0, result.state.economy.trophiesStock, "Stock should remain 0")
        assertEquals(100, result.state.economy.moneyCopper, "Money should not change")
        assertEquals(0, result.events.size, "Should emit no events")
    }

    @Test
    fun `sell exact stock amount works`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 20,
                trophiesStock = 4,
                reservedCopper = 0
            )
        )
        val rng = Rng(100L)
        val cmd = SellTrophies(amount = 4, cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(0, result.state.economy.trophiesStock, "Should sell all 4 trophies")
        assertEquals(24, result.state.economy.moneyCopper, "Should gain 4 copper")

        val event = result.events[0] as TrophySold
        assertEquals(4, event.amount)
        assertEquals(4, event.moneyGained)
    }

    @Test
    fun `sell more than stock sells only available stock`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 10,
                trophiesStock = 3,
                reservedCopper = 0
            )
        )
        val rng = Rng(100L)
        val cmd = SellTrophies(amount = 10, cmdId = 1L)

        // Validation should reject this
        val validationResult = canApply(state, cmd)
        assertTrue(validationResult is ValidationResult.Rejected)
    }

    @Test
    fun `validation accepts zero amount`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 0,
                trophiesStock = 5,
                reservedCopper = 0
            )
        )
        val cmd = SellTrophies(amount = 0, cmdId = 1L)

        val result = canApply(state, cmd)

        assertEquals(ValidationResult.Valid, result, "Amount 0 should be valid (sell all)")
    }

    @Test
    fun `validation accepts valid positive amount`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 0,
                trophiesStock = 5,
                reservedCopper = 0
            )
        )
        val cmd = SellTrophies(amount = 3, cmdId = 1L)

        val result = canApply(state, cmd)

        assertEquals(ValidationResult.Valid, result, "Valid amount should be accepted")
    }

    @Test
    fun `sell increments revision`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 0,
                trophiesStock = 1,
                reservedCopper = 0
            )
        )
        val rng = Rng(100L)
        val cmd = SellTrophies(amount = 1, cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(1L, result.state.meta.revision, "Revision should be incremented")
    }

    @Test
    fun `sell does not modify other state`() {
        val state = initialState(42u).copy(
            economy = EconomyState(
                moneyCopper = 100,
                trophiesStock = 5,
                reservedCopper = 0
            )
        )
        val rng = Rng(100L)
        val cmd = SellTrophies(amount = 2, cmdId = 1L)

        val result = step(state, cmd, rng)

        assertEquals(state.contracts, result.state.contracts, "Contracts should not change")
        assertEquals(state.heroes, result.state.heroes, "Heroes should not change")
        assertEquals(state.region, result.state.region, "Region should not change")
        assertEquals(state.guild, result.state.guild, "Guild should not change")
        assertEquals(state.meta.dayIndex, result.state.meta.dayIndex, "Day should not change")
    }
}
