package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/TESTING.md.

import core.AdvanceDay
import core.step
import core.rng.Rng
import test.helpers.assertStepOk
import test.helpers.baseState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Hero naming is part of determinism contract: same seeds + same commands => same names.
 */
@P1
class HeroNamesTest {

    private val pool = setOf("Smith", "Jos", "Anna", "White", "Jack")

    @Test
    fun `heroes arrived names are from the fixed pool`() {
        // GIVEN
        val state = baseState(seed = 42u)
        val rng = Rng(100L)

        // WHEN
        val result = step(state, AdvanceDay(cmdId = 1L), rng)

        // THEN
        assertStepOk(result.events, "advance_day")
        val heroes = result.state.heroes.roster
        assertTrue(heroes.isNotEmpty(), "At least one hero should arrive")
        assertTrue(heroes.all { it.name in pool }, "All hero names must be from the fixed pool")
    }

    @Test
    fun `hero names are deterministic for same state seed and rng seed`() {
        // GIVEN
        val stateSeed = 42u
        val rngSeed = 100L

        // WHEN
        val r1 = step(baseState(stateSeed), AdvanceDay(cmdId = 1L), Rng(rngSeed))
        val r2 = step(baseState(stateSeed), AdvanceDay(cmdId = 1L), Rng(rngSeed))

        // THEN
        assertStepOk(r1.events, "run1")
        assertStepOk(r2.events, "run2")

        val names1 = r1.state.heroes.roster.map { it.name }
        val names2 = r2.state.heroes.roster.map { it.name }
        assertEquals(names1, names2, "Same seeds must produce identical hero names in identical order")
        assertTrue(names1.all { it in pool }, "Names must be from the pool")
    }
}
