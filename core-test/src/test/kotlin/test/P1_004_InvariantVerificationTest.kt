// FILE: core-test/src/test/kotlin/test/P1_004_InvariantVerificationTest.kt
package test

// TEST LEVEL: P1 — Critical unit tests (priority P1). See core-test/README.md for test-level meaning.

import core.invariants.InvariantId
import core.primitives.*
import core.state.*
import kotlin.test.*

@P1
@Smoke
class P1_004_InvariantVerificationTest {

    private val seed = 42u

    @Test
    fun `initial state has no violations`() {
        assertStateValid(baseState(seed))
    }

    @Test
    fun `economy negatives`() {
        expectViolation(seed, InvariantId.ECONOMY__MONEY_NON_NEGATIVE) {
            copy(economy = EconomyState(moneyCopper = -100, trophiesStock = 0, reservedCopper = 0))
        }
        expectViolation(seed, InvariantId.ECONOMY__TROPHIES_NON_NEGATIVE) {
            copy(economy = EconomyState(moneyCopper = 0, trophiesStock = -50, reservedCopper = 0))
        }
    }

    @Test
    fun `bounds for stability and reputation`() {
        expectViolation(seed, InvariantId.REGION__STABILITY_0_100) { copy(region = RegionState(stability = -1)) }
        expectViolation(seed, InvariantId.REGION__STABILITY_0_100) { copy(region = RegionState(stability = 101)) }

        expectViolation(seed, InvariantId.GUILD__REPUTATION_0_100) {
            copy(guild = GuildState(guildRank = 1, reputation = -1, completedContractsTotal = 0, contractsForNextRank = 1))
        }
        expectViolation(seed, InvariantId.GUILD__REPUTATION_0_100) {
            copy(guild = GuildState(guildRank = 1, reputation = 101, completedContractsTotal = 0, contractsForNextRank = 1))
        }
    }

    @Test
    fun `id counters must be positive`() {
        expectViolation(seed, InvariantId.IDS__NEXT_CONTRACT_ID_POSITIVE) {
            copy(meta = meta.copy(ids = IdCounters(nextContractId = 0, nextHeroId = 1, nextActiveContractId = 1)))
        }
        expectViolation(seed, InvariantId.IDS__NEXT_HERO_ID_POSITIVE) {
            copy(meta = meta.copy(ids = IdCounters(nextContractId = 1, nextHeroId = 0, nextActiveContractId = 1)))
        }
        expectViolation(seed, InvariantId.IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE) {
            copy(meta = meta.copy(ids = IdCounters(nextContractId = 1, nextHeroId = 1, nextActiveContractId = 0)))
        }
    }

    @Test
    fun `nextContractId must exceed max contractId in inbox+board`() {
        expectViolation(seed, InvariantId.IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID) {
            copy(
                contracts = ContractState(
                    inbox = listOf(
                        ContractDraft(
                            id = ContractId(5),
                            createdDay = 0,
                            title = "Test",
                            rankSuggested = Rank.F,
                            feeOffered = 0,
                            salvage = SalvagePolicy.GUILD,
                            baseDifficulty = 1,
                            proofHint = "proof"
                        )
                    ),
                    board = emptyList(),
                    active = emptyList(),
                    returns = emptyList()
                ),
                meta = meta.copy(ids = IdCounters(nextContractId = 5, nextHeroId = meta.ids.nextHeroId, nextActiveContractId = meta.ids.nextActiveContractId))
            )
        }
    }

    @Test
    fun `active daysRemaining constraints`() {
        fun active(days: Int) = ActiveContract(
            id = ActiveContractId(1),
            boardContractId = ContractId(1),
            takenDay = 0,
            daysRemaining = days,
            heroIds = listOf(HeroId(1)),
            status = ActiveStatus.WIP
        )

        expectViolation(seed, InvariantId.CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE) {
            copy(contracts = contracts.copy(active = listOf(active(-1))))
        }

        expectViolation(seed, InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2) {
            copy(contracts = contracts.copy(active = listOf(active(0))))
        }
        expectViolation(seed, InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2) {
            copy(contracts = contracts.copy(active = listOf(active(3))))
        }
    }

    @Test
    fun `allows WIP daysRemaining 1 or 2`() {
        val hero = Hero(
            id = HeroId(1),
            name = "Test",
            rank = Rank.F,
            klass = HeroClass.WARRIOR,
            traits = Traits(50, 50, 50),
            status = HeroStatus.ON_MISSION,
            historyCompleted = 0
        )

        fun stateWith(days: Int) = state(seed) {
            copy(
                heroes = HeroState(roster = listOf(hero), arrivalsToday = emptyList()),
                contracts = ContractState(
                    inbox = emptyList(),
                    board = emptyList(),
                    active = listOf(
                        ActiveContract(
                            id = ActiveContractId(1),
                            boardContractId = ContractId(1),
                            takenDay = 0,
                            daysRemaining = days,
                            heroIds = listOf(HeroId(1)),
                            status = ActiveStatus.WIP
                        )
                    ),
                    returns = emptyList()
                )
            )
        }

        assertNoViolations(stateWith(1), InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2)
        assertNoViolations(stateWith(2), InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2)
    }

    @Test
    fun `allows informational return packet without active after auto-close`() {
        val s = state(seed) {
            copy(
                contracts = ContractState(
                    inbox = emptyList(),
                    board = emptyList(),
                    active = emptyList(),
                    returns = listOf(
                        ReturnPacket(
                            boardContractId = ContractId(1),
                            heroIds = listOf(HeroId(1)),
                            activeContractId = ActiveContractId(999),
                            resolvedDay = 1,
                            outcome = Outcome.SUCCESS,
                            trophiesCount = 0,
                            trophiesQuality = Quality.OK,
                            reasonTags = emptyList(),
                            requiresPlayerClose = false, // key: informational packet
                            suspectedTheft = false
                        )
                    )
                )
            )
        }

        assertNoViolations(s, InvariantId.CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE)

        val maybeRef = runCatching { InvariantId.valueOf("INV_REFERENTIAL_INTEGRITY") }.getOrNull()
        if (maybeRef != null) {
            assertNoViolations(s, maybeRef)
        }
    }

    @Test
    fun `violation details are deterministic`() {
        val s = state(seed) {
            copy(economy = EconomyState(moneyCopper = -100, trophiesStock = 0, reservedCopper = 0))
        }
        assertViolationDetailsDeterministic(s)
    }
}
