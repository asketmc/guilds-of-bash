package test

import core.invariants.InvariantId
import core.invariants.verifyInvariants
import core.primitives.*
import core.state.*
import kotlin.test.*

/**
 * P1 CRITICAL: Invariant verification tests.
 * State corruption detection is critical for game stability.
 */
class P1_InvariantVerificationTest {

    @Test
    fun `verifyInvariants returns empty list for valid initial state`() {
        val state = initialState(42u)

        val violations = verifyInvariants(state)

        assertTrue(violations.isEmpty(), "Initial state must have no violations")
    }

    @Test
    fun `verifyInvariants detects negative money`() {
        val state = initialState(42u).copy(
            economy = EconomyState(moneyCopper = -100, trophiesStock = 0)
        )

        val violations = verifyInvariants(state)

        assertTrue(violations.any { it.invariantId == InvariantId.ECONOMY__MONEY_NON_NEGATIVE })
    }

    @Test
    fun `verifyInvariants detects negative trophies`() {
        val state = initialState(42u).copy(
            economy = EconomyState(moneyCopper = 0, trophiesStock = -50)
        )

        val violations = verifyInvariants(state)

        assertTrue(violations.any { it.invariantId == InvariantId.ECONOMY__TROPHIES_NON_NEGATIVE })
    }

    @Test
    fun `verifyInvariants detects stability out of range`() {
        val stateBelow = initialState(42u).copy(
            region = RegionState(stability = -1)
        )
        val stateAbove = initialState(42u).copy(
            region = RegionState(stability = 101)
        )

        val violationsBelow = verifyInvariants(stateBelow)
        val violationsAbove = verifyInvariants(stateAbove)

        assertTrue(violationsBelow.any { it.invariantId == InvariantId.REGION__STABILITY_0_100 })
        assertTrue(violationsAbove.any { it.invariantId == InvariantId.REGION__STABILITY_0_100 })
    }

    @Test
    fun `verifyInvariants detects reputation out of range`() {
        val stateBelow = initialState(42u).copy(
            guild = GuildState(guildRank = 1, reputation = -1)
        )
        val stateAbove = initialState(42u).copy(
            guild = GuildState(guildRank = 1, reputation = 101)
        )

        val violationsBelow = verifyInvariants(stateBelow)
        val violationsAbove = verifyInvariants(stateAbove)

        assertTrue(violationsBelow.any { it.invariantId == InvariantId.GUILD__REPUTATION_0_100 })
        assertTrue(violationsAbove.any { it.invariantId == InvariantId.GUILD__REPUTATION_0_100 })
    }

    @Test
    fun `verifyInvariants detects nextContractId not positive`() {
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(
                ids = IdCounters(nextContractId = 0, nextHeroId = 1, nextActiveContractId = 1)
            )
        )

        val violations = verifyInvariants(state)

        assertTrue(violations.any { it.invariantId == InvariantId.IDS__NEXT_CONTRACT_ID_POSITIVE })
    }

    @Test
    fun `verifyInvariants detects nextHeroId not positive`() {
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(
                ids = IdCounters(nextContractId = 1, nextHeroId = 0, nextActiveContractId = 1)
            )
        )

        val violations = verifyInvariants(state)

        assertTrue(violations.any { it.invariantId == InvariantId.IDS__NEXT_HERO_ID_POSITIVE })
    }

    @Test
    fun `verifyInvariants detects nextActiveContractId not positive`() {
        val state = initialState(42u).copy(
            meta = initialState(42u).meta.copy(
                ids = IdCounters(nextContractId = 1, nextHeroId = 1, nextActiveContractId = 0)
            )
        )

        val violations = verifyInvariants(state)

        assertTrue(violations.any { it.invariantId == InvariantId.IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE })
    }

    @Test
    fun `verifyInvariants detects nextContractId not greater than max contract id`() {
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = listOf(
                    ContractDraft(
                        id = ContractId(5),
                        createdDay = 0,
                        title = "Test",
                        rankSuggested = Rank.F,
                        feeOffered = 0,
                        baseDifficulty = 1,
                        proofHint = "proof"
                    )
                ),
                board = emptyList(),
                active = emptyList(),
                returns = emptyList()
            ),
            meta = initialState(42u).meta.copy(
                ids = IdCounters(nextContractId = 5, nextHeroId = 1, nextActiveContractId = 1)
            )
        )

        val violations = verifyInvariants(state)

        assertTrue(violations.any { it.invariantId == InvariantId.IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID })
    }

    @Test
    fun `verifyInvariants detects negative daysRemaining in active contract`() {
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 0,
                        daysRemaining = -1,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            )
        )

        val violations = verifyInvariants(state)

        assertTrue(violations.any { it.invariantId == InvariantId.CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE })
    }

    @Test
    fun `verifyInvariants detects WIP contract with invalid daysRemaining`() {
        val stateZero = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 0,
                        daysRemaining = 0,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            )
        )

        val stateThree = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 0,
                        daysRemaining = 3,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            )
        )

        val violationsZero = verifyInvariants(stateZero)
        val violationsThree = verifyInvariants(stateThree)

        assertTrue(violationsZero.any { it.invariantId == InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2 })
        assertTrue(violationsThree.any { it.invariantId == InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2 })
    }

    @Test
    fun `verifyInvariants allows WIP contract with daysRemaining 1 or 2`() {
        val state1 = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = listOf(
                    ActiveContract(
                        id = ActiveContractId(1),
                        boardContractId = ContractId(1),
                        takenDay = 0,
                        daysRemaining = 1,
                        heroIds = listOf(HeroId(1)),
                        status = ActiveStatus.WIP
                    )
                ),
                returns = emptyList()
            ),
            heroes = HeroState(
                roster = listOf(
                    Hero(
                        id = HeroId(1),
                        name = "Test",
                        rank = Rank.F,
                        klass = HeroClass.WARRIOR,
                        traits = Traits(50, 50, 50),
                        status = HeroStatus.ON_MISSION,
                        historyCompleted = 0
                    )
                ),
                arrivalsToday = emptyList()
            )
        )

        val state2 = state1.copy(
            contracts = state1.contracts.copy(
                active = listOf(state1.contracts.active[0].copy(daysRemaining = 2))
            )
        )

        val violations1 = verifyInvariants(state1)
        val violations2 = verifyInvariants(state2)

        assertFalse(violations1.any { it.invariantId == InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2 })
        assertFalse(violations2.any { it.invariantId == InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2 })
    }

    @Test
    fun `verifyInvariants detects return packet without active contract`() {
        val state = initialState(42u).copy(
            contracts = ContractState(
                inbox = emptyList(),
                board = emptyList(),
                active = emptyList(),
                returns = listOf(
                    ReturnPacket(
                        activeContractId = ActiveContractId(999),
                        resolvedDay = 1,
                        outcome = Outcome.SUCCESS,
                        trophiesCount = 0,
                        trophiesQuality = Quality.OK,
                        reasonTags = emptyList(),
                        requiresPlayerClose = true
                    )
                )
            )
        )

        val violations = verifyInvariants(state)

        assertTrue(violations.any { it.invariantId == InvariantId.CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE })
    }

    @Test
    fun `verifyInvariants provides stable detail strings`() {
        val state = initialState(42u).copy(
            economy = EconomyState(moneyCopper = -100, trophiesStock = 0)
        )

        val violations1 = verifyInvariants(state)
        val violations2 = verifyInvariants(state)

        assertEquals(violations1.size, violations2.size)
        violations1.zip(violations2).forEach { (v1, v2) ->
            assertEquals(v1.invariantId, v2.invariantId)
            assertEquals(v1.details, v2.details, "Violation details must be deterministic")
        }
    }
}
