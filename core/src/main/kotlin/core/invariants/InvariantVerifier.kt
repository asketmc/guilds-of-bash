package core.invariants

import core.primitives.*
import core.state.*

fun verifyInvariants(state: GameState): List<InvariantViolation> {
    val violations = mutableListOf<InvariantViolation>()

    // --- 1. Indices & Precomputations ---
    val inboxIds = state.contracts.inbox.map { it.id.value }
    val boardIds = state.contracts.board.map { it.id.value }
    val allContractIds = inboxIds + boardIds
    val maxContractId = allContractIds.maxOrNull() ?: 0

    val activeIds = state.contracts.active.map { it.id.value }
    val maxActiveId = activeIds.maxOrNull() ?: 0
    val activeIdSet = activeIds.toHashSet()

    val returnsByActiveId = state.contracts.returns.groupBy { it.activeContractId.value }
    val activeByBoardId = state.contracts.active.groupBy { it.boardContractId.value }

    val heroesById = state.heroes.roster.associateBy { it.id.value }
    val maxHeroId = heroesById.keys.maxOrNull() ?: 0

    val heroUsageCount = mutableMapOf<Int, Int>()
    state.contracts.active.forEach { active ->
        active.heroIds.forEach { heroId ->
            heroUsageCount[heroId.value] = (heroUsageCount[heroId.value] ?: 0) + 1
        }
    }

    // --- 2. Invariants Check (ordered by InvariantId enum) ---

    // IDS__NEXT_CONTRACT_ID_POSITIVE
    if (state.meta.ids.nextContractId <= 0) {
        violations.add(
            InvariantViolation(
                InvariantId.IDS__NEXT_CONTRACT_ID_POSITIVE,
                "expected=nextContractId>0; nextContractId=${state.meta.ids.nextContractId}"
            )
        )
    }

    // IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE
    if (state.meta.ids.nextActiveContractId <= 0) {
        violations.add(
            InvariantViolation(
                InvariantId.IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE,
                "expected=nextActiveContractId>0; nextActiveContractId=${state.meta.ids.nextActiveContractId}"
            )
        )
    }

    // IDS__NEXT_HERO_ID_POSITIVE
    if (state.meta.ids.nextHeroId <= 0) {
        violations.add(
            InvariantViolation(
                InvariantId.IDS__NEXT_HERO_ID_POSITIVE,
                "expected=nextHeroId>0; nextHeroId=${state.meta.ids.nextHeroId}"
            )
        )
    }

    // IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID
    if (state.meta.ids.nextContractId <= maxContractId) {
        violations.add(
            InvariantViolation(
                InvariantId.IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID,
                "expected=nextContractId>maxContractId; nextContractId=${state.meta.ids.nextContractId}; maxContractId=$maxContractId"
            )
        )
    }

    // IDS__NEXT_ACTIVE_CONTRACT_ID_GT_MAX_ACTIVE_ID
    if (state.meta.ids.nextActiveContractId <= maxActiveId) {
        violations.add(
            InvariantViolation(
                InvariantId.IDS__NEXT_ACTIVE_CONTRACT_ID_GT_MAX_ACTIVE_ID,
                "expected=nextActiveContractId>maxActiveId; nextActiveContractId=${state.meta.ids.nextActiveContractId}; maxActiveId=$maxActiveId"
            )
        )
    }

    // IDS__NEXT_HERO_ID_GT_MAX_HERO_ID
    if (state.meta.ids.nextHeroId <= maxHeroId) {
        violations.add(
            InvariantViolation(
                InvariantId.IDS__NEXT_HERO_ID_GT_MAX_HERO_ID,
                "expected=nextHeroId>maxHeroId; nextHeroId=${state.meta.ids.nextHeroId}; maxHeroId=$maxHeroId"
            )
        )
    }

    // CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE
    state.contracts.board
        .asSequence()
        .filter { it.status == BoardStatus.LOCKED }
        .sortedBy { it.id.value }
        .forEach { board ->
            val actives = activeByBoardId[board.id.value] ?: emptyList()
            val hasNonClosed = actives.any { it.status != ActiveStatus.CLOSED }
            if (!hasNonClosed) {
                violations.add(
                    InvariantViolation(
                        InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE,
                        "boardId=${board.id.value}; expected=lockedBoardHasNonClosedActive"
                    )
                )
            }
        }

    // CONTRACTS__RETURN_READY_HAS_RETURN_PACKET
    state.contracts.active
        .asSequence()
        .filter { it.status == ActiveStatus.RETURN_READY }
        .sortedBy { it.id.value }
        .forEach { active ->
            if (returnsByActiveId[active.id.value].isNullOrEmpty()) {
                violations.add(
                    InvariantViolation(
                        InvariantId.CONTRACTS__RETURN_READY_HAS_RETURN_PACKET,
                        "activeId=${active.id.value}; expected=returnReadyHasReturnPacket"
                    )
                )
            }
        }

    // CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE
    state.contracts.returns
        .sortedWith(compareBy<ReturnPacket>({ it.activeContractId.value }, { it.resolvedDay }, { it.trophiesCount }))
        .forEach { packet ->
            if (!activeIdSet.contains(packet.activeContractId.value)) {
                violations.add(
                    InvariantViolation(
                        InvariantId.CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE,
                        "activeId=${packet.activeContractId.value}; expected=returnPacketRefsExistingActive"
                    )
                )
            }
        }

    // CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE
    state.contracts.active
        .asSequence()
        .filter { it.daysRemaining < 0 }
        .sortedBy { it.id.value }
        .forEach { active ->
            violations.add(
                InvariantViolation(
                    InvariantId.CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE,
                    "activeId=${active.id.value}; daysRemaining=${active.daysRemaining}; expected=daysRemaining>=0"
                )
            )
        }

    // CONTRACTS__WIP_DAYS_REMAINING_IN_1_2
    state.contracts.active
        .asSequence()
        .filter { it.status == ActiveStatus.WIP && it.daysRemaining !in 1..2 }
        .sortedBy { it.id.value }
        .forEach { active ->
            violations.add(
                InvariantViolation(
                    InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2,
                    "activeId=${active.id.value}; status=WIP; daysRemaining=${active.daysRemaining}; expected=1..2"
                )
            )
        }

    // HEROES__ON_MISSION_IN_EXACTLY_ONE_ACTIVE_CONTRACT
    state.heroes.roster
        .asSequence()
        .filter { it.status == HeroStatus.ON_MISSION }
        .sortedBy { it.id.value }
        .forEach { hero ->
            val count = heroUsageCount[hero.id.value] ?: 0
            if (count != 1) {
                violations.add(
                    InvariantViolation(
                        InvariantId.HEROES__ON_MISSION_IN_EXACTLY_ONE_ACTIVE_CONTRACT,
                        "heroId=${hero.id.value}; expected=usageCount==1; usageCount=$count"
                    )
                )
            }
        }

    // HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION
    state.contracts.active
        .asSequence()
        .filter { it.status == ActiveStatus.WIP || it.status == ActiveStatus.RETURN_READY }
        .sortedBy { it.id.value }
        .forEach { active ->
            active.heroIds
                .sortedBy { it.value }
                .forEach { heroId ->
                    val hero = heroesById[heroId.value]
                    if (hero == null) {
                        violations.add(
                            InvariantViolation(
                                InvariantId.HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION,
                                "activeId=${active.id.value}; heroId=${heroId.value}; expected=heroExists"
                            )
                        )
                    } else if (hero.status != HeroStatus.ON_MISSION) {
                        violations.add(
                            InvariantViolation(
                                InvariantId.HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION,
                                "activeId=${active.id.value}; heroId=${heroId.value}; heroStatus=${hero.status}; expected=ON_MISSION"
                            )
                        )
                    }
                }
        }

    // ECONOMY__MONEY_NON_NEGATIVE
    if (state.economy.moneyCopper < 0) {
        violations.add(
            InvariantViolation(
                InvariantId.ECONOMY__MONEY_NON_NEGATIVE,
                "moneyCopper=${state.economy.moneyCopper}; expected>=0"
            )
        )
    }

    // ECONOMY__TROPHIES_NON_NEGATIVE
    if (state.economy.trophiesStock < 0) {
        violations.add(
            InvariantViolation(
                InvariantId.ECONOMY__TROPHIES_NON_NEGATIVE,
                "trophiesStock=${state.economy.trophiesStock}; expected>=0"
            )
        )
    }

    // REGION__STABILITY_0_100
    if (state.region.stability !in 0..100) {
        violations.add(
            InvariantViolation(
                InvariantId.REGION__STABILITY_0_100,
                "stability=${state.region.stability}; expected=0..100"
            )
        )
    }

    // GUILD__REPUTATION_0_100
    if (state.guild.reputation !in 0..100) {
        violations.add(
            InvariantViolation(
                InvariantId.GUILD__REPUTATION_0_100,
                "reputation=${state.guild.reputation}; expected=0..100"
            )
        )
    }

    return violations
}
