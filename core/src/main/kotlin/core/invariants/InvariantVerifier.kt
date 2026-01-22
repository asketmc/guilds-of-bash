// FILE: core/src/main/kotlin/core/invariants/InvariantVerifier.kt
package core.invariants

import core.primitives.*
import core.state.*

/**
 * Verifies a subset of state consistency invariants and reports violations.
 *
 * Contract:
 * - Reads `state`, returns deterministic list of `InvariantViolation`.
 * - Check execution order is fixed (as written).
 * - Multi-violation checks stabilize ordering via explicit sorting by stable keys.
 * - Does not mutate `state`.
 */
fun verifyInvariants(state: GameState): List<InvariantViolation> {
    val violations = mutableListOf<InvariantViolation>()

    fun add(id: InvariantId, details: String) {
        violations.add(InvariantViolation(id, details))
    }

    /**
     * Enables adding new (aggregate) invariant checks without requiring enum changes.
     * If the enum constant does not exist, the check is a no-op.
     */
    fun addOptional(idName: String, details: String) {
        val id = runCatching { InvariantId.valueOf(idName) }.getOrNull() ?: return
        add(id, details)
    }

    // --- 1) Indices & Precomputations ---
    val inboxIds: List<Int> = state.contracts.inbox.map { it.id.value }
    val boardIds: List<Int> = state.contracts.board.map { it.id.value }
    val allContractIds: List<Int> = inboxIds + boardIds
    val maxContractId: Int = allContractIds.maxOrNull() ?: 0

    val activeIds: List<Int> = state.contracts.active.map { it.id.value }
    val maxActiveId: Int = activeIds.maxOrNull() ?: 0

    val heroesById: Map<Int, Hero> = state.heroes.roster.associateBy { it.id.value }
    val maxHeroId: Int = heroesById.keys.maxOrNull() ?: 0

    val activesByBoardId: Map<Int, List<ActiveContract>> =
        state.contracts.active.groupBy { it.boardContractId.value }

    val returnsByActiveId: Map<Int, List<ReturnPacket>> =
        state.contracts.returns.groupBy { it.activeContractId.value }

    fun formatActive(a: ActiveContract): String =
        "{id=${a.id.value},board=${a.boardContractId.value},status=${a.status},days=${a.daysRemaining},heroes=${
            a.heroIds.asSequence().map { it.value }.sorted().toList()
        }}"

    fun formatReturn(r: ReturnPacket): String =
        "{active=${r.activeContractId.value},board=${r.boardContractId.value},requiresClose=${r.requiresPlayerClose},trophies=${r.trophiesCount},q=${r.trophiesQuality}}"

    // Heroes "in flight" usage:
    // - WIP actives keep heroes ON_MISSION
    // - RETURN_READY actives keep heroes ON_MISSION until close
    val heroUsageCount: Map<Int, Int> = run {
        val m = mutableMapOf<Int, Int>()
        state.contracts.active
            .asSequence()
            .filter { it.status == ActiveStatus.WIP || it.status == ActiveStatus.RETURN_READY }
            .forEach { active ->
                active.heroIds.forEach { hid ->
                    val key = hid.value
                    m[key] = (m[key] ?: 0) + 1
                }
            }
        m
    }

    // --- 2) ID invariants ---
    run {
        val nextContractId: Int = state.meta.ids.nextContractId
        val nextActiveId: Int = state.meta.ids.nextActiveContractId
        val nextHeroId: Int = state.meta.ids.nextHeroId

        if (nextContractId <= 0) add(InvariantId.IDS__NEXT_CONTRACT_ID_POSITIVE, "nextContractId=$nextContractId")
        if (nextActiveId <= 0) add(InvariantId.IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE, "nextActiveContractId=$nextActiveId")
        if (nextHeroId <= 0) add(InvariantId.IDS__NEXT_HERO_ID_POSITIVE, "nextHeroId=$nextHeroId")

        if (nextContractId <= maxContractId) {
            add(
                InvariantId.IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID,
                "nextContractId=$nextContractId must be > maxContractId=$maxContractId (inbox+board)"
            )
        }
        if (nextActiveId <= maxActiveId) {
            add(
                InvariantId.IDS__NEXT_ACTIVE_CONTRACT_ID_GT_MAX_ACTIVE_ID,
                "nextActiveContractId=$nextActiveId must be > maxActiveId=$maxActiveId (active)"
            )
        }
        if (nextHeroId <= maxHeroId) {
            add(
                InvariantId.IDS__NEXT_HERO_ID_GT_MAX_HERO_ID,
                "nextHeroId=$nextHeroId must be > maxHeroId=$maxHeroId (roster)"
            )
        }
    }

    // --- Optional aggregate invariants (only if enum contains these names) ---
    // INV_UNIQUE_IDS, INV_REFERENTIAL_INTEGRITY, INV_SORT_ORDER_STABILITY, INV_BOARD_ACTIVE_EXCLUSION

    fun findDuplicateInts(ids: List<Int>): List<Pair<Int, Int>> =
        ids.groupingBy { it }
            .eachCount()
            .asSequence()
            .filter { it.value > 1 }
            .sortedBy { it.key }
            .map { it.key to it.value }
            .toList()

    run {
        val dupInbox = findDuplicateInts(inboxIds)
        val dupBoard = findDuplicateInts(boardIds)
        val dupActive = findDuplicateInts(activeIds)
        val dupHero = findDuplicateInts(state.heroes.roster.map { it.id.value })

        if (dupInbox.isNotEmpty() || dupBoard.isNotEmpty() || dupActive.isNotEmpty() || dupHero.isNotEmpty()) {
            val details = buildString {
                if (dupInbox.isNotEmpty()) append("inbox=").append(dupInbox)
                if (dupBoard.isNotEmpty()) { if (isNotEmpty()) append(" "); append("board=").append(dupBoard) }
                if (dupActive.isNotEmpty()) { if (isNotEmpty()) append(" "); append("active=").append(dupActive) }
                if (dupHero.isNotEmpty()) { if (isNotEmpty()) append(" "); append("heroes=").append(dupHero) }
            }
            addOptional("INV_UNIQUE_IDS", details)
        }
    }

    run {
        val boardIdSet: Set<Int> = state.contracts.board.asSequence().map { it.id.value }.toSet()
        val heroIdSet: Set<Int> = heroesById.keys
        val activeIdSet: Set<Int> = state.contracts.active.asSequence().map { it.id.value }.toSet()

        val issues = mutableListOf<String>()

        state.contracts.active
            .asSequence()
            .sortedBy { it.id.value }
            .forEach { active ->
                if (active.boardContractId.value !in boardIdSet) {
                    issues.add("activeId=${active.id.value} boardContractId=${active.boardContractId.value} not found on board")
                }
                active.heroIds
                    .asSequence()
                    .map { it.value }
                    .sorted()
                    .forEach { hid ->
                        if (hid !in heroIdSet) {
                            issues.add("activeId=${active.id.value} references missing heroId=$hid")
                        }
                    }
            }

        state.contracts.returns
            .asSequence()
            .sortedWith(compareBy<ReturnPacket> { it.activeContractId.value }.thenBy { it.boardContractId.value })
            .forEach { r ->
                if (r.activeContractId.value !in activeIdSet) {
                    issues.add("return.activeId=${r.activeContractId.value} missing; return=${formatReturn(r)}")
                }
                if (r.boardContractId.value !in boardIdSet) {
                    issues.add("return.boardId=${r.boardContractId.value} missing; return=${formatReturn(r)}")
                }
            }

        if (issues.isNotEmpty()) {
            addOptional("INV_REFERENTIAL_INTEGRITY", issues.joinToString(separator = " | "))
        }
    }

    run {
        fun isSortedAscendingInts(ids: List<Int>): Boolean {
            if (ids.size <= 1) return true
            var prev = ids[0]
            for (i in 1 until ids.size) {
                val cur = ids[i]
                if (cur < prev) return false
                prev = cur
            }
            return true
        }

        val bad = mutableListOf<String>()

        if (!isSortedAscendingInts(state.contracts.board.map { it.id.value })) bad.add("board:not_sorted_by_id")
        if (!isSortedAscendingInts(state.contracts.active.map { it.id.value })) bad.add("active:not_sorted_by_id")
        if (!isSortedAscendingInts(state.heroes.roster.map { it.id.value })) bad.add("heroes.roster:not_sorted_by_id")

        val returnKeys: List<Triple<Int, Int, Int>> =
            state.contracts.returns.map { Triple(it.activeContractId.value, it.boardContractId.value, it.resolvedDay) }
        if (returnKeys.size > 1) {
            val sortedKeys = returnKeys.sortedWith(
                compareBy<Triple<Int, Int, Int>> { it.first }.thenBy { it.second }.thenBy { it.third }
            )
            if (sortedKeys != returnKeys) bad.add("returns:not_sorted_by_active_board_resolvedDay")
        }

        if (bad.isNotEmpty()) addOptional("INV_SORT_ORDER_STABILITY", bad.joinToString(","))
    }

    // --- 3) Contracts invariants ---

    // 3.1) LOCKED board must be referenced by exactly one NON-CLOSED active.
    state.contracts.board
        .asSequence()
        .sortedBy { it.id.value }
        .forEach { board ->
            val activesNonClosed = (activesByBoardId[board.id.value] ?: emptyList())
                .asSequence()
                .filter { it.status != ActiveStatus.CLOSED }
                .sortedBy { it.id.value }
                .toList()

            if (board.status == BoardStatus.LOCKED) {
                if (activesNonClosed.size != 1) {
                    add(
                        InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE,
                        buildString {
                            append("boardId=").append(board.id.value)
                            append(" status=LOCKED requires exactly 1 non-closed active, got=").append(activesNonClosed.size)
                            append(" actives=").append(activesNonClosed.joinToString(prefix = "[", postfix = "]") { formatActive(it) })
                        }
                    )
                }
            } else {
                // Mirror check (optional aggregate invariant)
                if (activesNonClosed.isNotEmpty()) {
                    addOptional(
                        "INV_BOARD_ACTIVE_EXCLUSION",
                        "boardId=${board.id.value} status=${board.status} has non-closed actives=${
                            activesNonClosed.joinToString(prefix = "[", postfix = "]") { formatActive(it) }
                        }"
                    )
                }
            }
        }

    // 3.2) RETURN_READY active must have exactly one return packet.
    state.contracts.active
        .asSequence()
        .filter { it.status == ActiveStatus.RETURN_READY }
        .sortedBy { it.id.value }
        .forEach { active ->
            val packets = (returnsByActiveId[active.id.value] ?: emptyList()).sortedWith(
                compareBy<ReturnPacket> { it.resolvedDay }.thenBy { it.boardContractId.value }
            )

            if (packets.size != 1) {
                add(
                    InvariantId.CONTRACTS__RETURN_READY_HAS_RETURN_PACKET,
                    buildString {
                        append("activeId=").append(active.id.value)
                        append(" status=RETURN_READY must have exactly 1 ReturnPacket, got=").append(packets.size)
                        append(" packets=").append(packets.joinToString(prefix = "[", postfix = "]") { formatReturn(it) })
                    }
                )
            }
        }

    // 3.3) Each return packet must point to an existing active.
    val activeIdSet: Set<Int> = state.contracts.active.asSequence().map { it.id.value }.toSet()
    state.contracts.returns
        .asSequence()
        .sortedWith(compareBy<ReturnPacket> { it.activeContractId.value }.thenBy { it.boardContractId.value })
        .forEach { packet ->
            if (packet.activeContractId.value !in activeIdSet) {
                add(
                    InvariantId.CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE,
                    "returnPacket.activeId=${packet.activeContractId.value} not found in actives; packet=${formatReturn(packet)}"
                )
            }
        }

    // 3.4) Active daysRemaining must be non-negative.
    state.contracts.active
        .asSequence()
        .sortedBy { it.id.value }
        .forEach { active ->
            if (active.daysRemaining < 0) {
                add(
                    InvariantId.CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE,
                    "activeId=${active.id.value} daysRemaining=${active.daysRemaining}"
                )
            }
        }

    // 3.5) WIP daysRemaining must be in 1..2.
    state.contracts.active
        .asSequence()
        .filter { it.status == ActiveStatus.WIP }
        .sortedBy { it.id.value }
        .forEach { active ->
            if (active.daysRemaining !in 1..2) {
                add(
                    InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2,
                    "activeId=${active.id.value} status=WIP daysRemaining=${active.daysRemaining} (expected 1..2)"
                )
            }
        }

    // --- 4) Heroes invariants ---

    // 4.1) Heroes referenced by WIP/RETURN_READY actives must be ON_MISSION.
    state.contracts.active
        .asSequence()
        .filter { it.status == ActiveStatus.WIP || it.status == ActiveStatus.RETURN_READY }
        .sortedBy { it.id.value }
        .forEach { active ->
            active.heroIds
                .asSequence()
                .map { it.value }
                .sorted()
                .forEach { heroId ->
                    val hero = heroesById[heroId]
                    if (hero == null) {
                        add(
                            InvariantId.HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION,
                            "activeId=${active.id.value} references missing heroId=$heroId"
                        )
                    } else if (hero.status != HeroStatus.ON_MISSION) {
                        add(
                            InvariantId.HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION,
                            "heroId=$heroId must be ON_MISSION while activeId=${active.id.value} is ${active.status}, got=${hero.status}"
                        )
                    }
                }
        }

    // 4.2) Each ON_MISSION hero must be used in exactly one WIP/RETURN_READY active.
    state.heroes.roster
        .asSequence()
        .sortedBy { it.id.value }
        .forEach { hero ->
            if (hero.status == HeroStatus.ON_MISSION) {
                val count = heroUsageCount[hero.id.value] ?: 0
                if (count != 1) {
                    add(
                        InvariantId.HEROES__ON_MISSION_IN_EXACTLY_ONE_ACTIVE_CONTRACT,
                        "heroId=${hero.id.value} status=ON_MISSION must appear in exactly 1 active (WIP/RETURN_READY), got=$count"
                    )
                }
            }
        }

    // --- 5) Economy / Region / Guild invariants ---
    if (state.economy.moneyCopper < 0) {
        add(InvariantId.ECONOMY__MONEY_NON_NEGATIVE, "moneyCopper=${state.economy.moneyCopper}")
    }
    if (state.economy.trophiesStock < 0) {
        add(InvariantId.ECONOMY__TROPHIES_NON_NEGATIVE, "trophiesStock=${state.economy.trophiesStock}")
    }
    if (state.economy.reservedCopper < 0) {
        add(InvariantId.ECONOMY__RESERVED_NON_NEGATIVE, "reservedCopper=${state.economy.reservedCopper}")
    }
    run {
        val available = state.economy.moneyCopper - state.economy.reservedCopper
        if (available < 0) {
            add(
                InvariantId.ECONOMY__AVAILABLE_NON_NEGATIVE,
                "available=$available (moneyCopper=${state.economy.moneyCopper} - reservedCopper=${state.economy.reservedCopper})"
            )
        }
    }

    if (state.region.stability !in 0..100) {
        add(InvariantId.REGION__STABILITY_0_100, "stability=${state.region.stability}")
    }

    if (state.guild.reputation !in 0..100) {
        add(InvariantId.GUILD__REPUTATION_0_100, "reputation=${state.guild.reputation}")
    }

    return violations
}
