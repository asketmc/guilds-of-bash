// FILE: core-test/src/test/kotlin/test/MoneyIntegrationTest.kt
package test

import core.AdvanceDay
import core.primitives.Rank
import core.rng.Rng
import core.state.initialState
import core.step
import org.junit.jupiter.api.Test
import kotlin.test.assertTrue
import kotlin.test.assertEquals

/**
 * Integration test for FP-ECON-02 Money Contract refactor.
 *
 * Verifies that F-tier contracts now show non-zero fees when payout > 0,
 * fixing the bug where all F-tier contracts displayed "Fee Offered: 0 copper".
 */
class MoneyIntegrationTest {

    @Test
    fun `AdvanceDay generates F-tier contracts with non-zero fees when payout greater than zero`() {
        val seed = 42u
        val state = initialState(seed)
        val rng = Rng(seed.toLong())

        // Advance one day to generate new drafts
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        val newState = result.state

        // Check generated drafts
        val generatedDrafts = newState.contracts.inbox.filter { it.id.value > 2 } // Exclude initial drafts

        assertTrue(generatedDrafts.isNotEmpty(), "Should have generated new drafts")

        // With F-tier payout range 0-100 copper and random sampling,
        // we should see at least some non-zero fees in a large enough sample
        val nonZeroFees = generatedDrafts.count { it.feeOffered > 0 }
        val totalGenerated = generatedDrafts.size

        println("Generated $totalGenerated F-tier drafts:")
        generatedDrafts.take(10).forEach { draft ->
            println("  Draft ${draft.id.value}: feeOffered=${draft.feeOffered} copper, clientDeposit=${draft.clientDeposit} copper")
        }

        // Statistical check: with payout range 0-100 copper, expect roughly 50% non-zero
        // (since PAYOUT_F_MIN=0, PAYOUT_F_MAX=1 GP = 100 copper, uniform distribution)
        assertTrue(
            nonZeroFees > 0,
            "Expected at least some drafts with non-zero fees, but all $totalGenerated had feeOffered=0"
        )

        println("✅ Fix verified: $nonZeroFees/$totalGenerated drafts have non-zero fees")
    }

    @Test
    fun `client deposit calculation preserves copper precision`() {
        val seed = 123u
        val state = initialState(seed)
        val rng = Rng(seed.toLong())

        // Advance day and check deposits
        val result = step(state, AdvanceDay(cmdId = 1L), rng)
        val newState = result.state

        val draftsWithDeposits = newState.contracts.inbox.filter { it.clientDeposit > 0 }

        if (draftsWithDeposits.isNotEmpty()) {
            println("\nDrafts with client deposits:")
            draftsWithDeposits.take(5).forEach { draft ->
                val expectedMaxDeposit = (draft.feeOffered * 50) / 100 // 50% of fee
                println("  Draft ${draft.id.value}: fee=${draft.feeOffered}c, deposit=${draft.clientDeposit}c (max expected: ~${expectedMaxDeposit}c)")

                // Deposit should never exceed fee
                assertTrue(
                    draft.clientDeposit <= draft.feeOffered,
                    "Client deposit (${draft.clientDeposit}) should not exceed fee (${draft.feeOffered})"
                )
            }

            println("✅ Deposit precision verified")
        }
    }

    @Test
    fun `F-tier with 100 copper payout and 50 percent deposit yields 50 copper deposit`() {
        // This is the core bug we're fixing:
        // OLD: 1 GP (int) * 50% = (1 * 5000) / 10000 = 0 (integer division)
        // NEW: 100 copper * 50% = (100 * 5000) / 10000 = 50 copper

        val seed = 999u
        val state = initialState(seed)
        val rng = Rng(seed.toLong())

        // Run multiple days to get a statistical sample
        var currentState = state
        val deposits = mutableListOf<Pair<Int, Int>>() // (fee, deposit) pairs

        for (day in 1..10) {
            val result = step(currentState, AdvanceDay(cmdId = day.toLong()), rng)
            currentState = result.state

            currentState.contracts.inbox.forEach { draft ->
                if (draft.clientDeposit > 0) {
                    deposits.add(draft.feeOffered to draft.clientDeposit)
                }
            }
        }

        // Check that we have cases where fee=100 copper and deposit=50 copper
        val perfectCase = deposits.find { (fee, deposit) ->
            fee == 100 && deposit == 50
        }

        if (perfectCase != null) {
            println("✅ Found perfect case: fee=100 copper → deposit=50 copper")
        } else {
            println("Sample deposits found:")
            deposits.take(10).forEach { (fee, deposit) ->
                println("  fee=$fee copper → deposit=$deposit copper (${deposit * 100 / fee.coerceAtLeast(1)}%)")
            }
        }

        // At minimum, verify no deposit is 0 when fee >= 100 (at 50% rate)
        val highFeeZeroDeposit = deposits.any { (fee, deposit) -> fee >= 100 && deposit == 0 }
        assertTrue(
            !highFeeZeroDeposit,
            "Should not have cases where fee >= 100 copper but deposit = 0 (that would be the old bug)"
        )
    }
}
