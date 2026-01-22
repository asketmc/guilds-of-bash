// FILE: core/src/main/kotlin/core/invariants/InvariantVerifier.kt
package core.invariants

import core.primitives.*
import core.state.*

/**
 * Verifies a subset of state consistency invariants and reports violations.
 *
 * ## Contract
 * - Reads the provided `state` and returns a list of `InvariantViolation` entries.
 * - Each `InvariantViolation.details` string is constructed deterministically from observed values.
 * - Violation emission order is deterministic:
 *   - Checks are executed in a fixed sequence (as written in this function).
 *   - Where multiple entities can violate the same invariant, the function sorts by stable keys
 *     (numeric id projections) before emitting violations.
 *
 * ## Determinism
 * - Deterministic for a given `state` (no RNG).
 */
fun verifyInvariants(state: GameState): List<InvariantViolation> {
    val violations = mutableListOf<InvariantViolation>()

    fun add(id: InvariantId, details: String) {
        violations.add(InvariantViolation(id, details))
    }

    /**
     * Stable numeric projection for id-like values.
     *
     * We intentionally avoid requiring ids to be Comparable or to expose a specific `.value` field.
     * For Kotlin value classes / wrappers, `hashCode()` is stable and (in typical id wrappers)
     * equals the underlying numeric value.
     */
    fun num(x: Any?): Int = when (x) {
        null -> 0
        is Int -> x
        is Long -> x.toInt()
        is UInt -> x.toInt()
        is ULong -> x.toInt()
        is Short -> x.toInt()
        is Byte -> x.toInt()
        else -> x.hashCode()
    }

    fun fmtReturn(p: ReturnPacket): String =
        "{active=${num(p.activeContractId)},board=${num(p.boardContractId)}," +
                "requiresClose=${p.requiresPlayerClose},trophies=${p.trophiesCount},q=${p.trophiesQuality}}"

    // --- 1) Precompute max ids for monotonicity checks ---
    val maxInboxContractId = state.contracts.inbox.maxOfOrNull { num(it.id) } ?: 0
    val maxBoardContractId = state.contracts.board.maxOfOrNull { num(it.id) } ?: 0
    val maxContractId = maxOf(maxInboxContractId, maxBoardContractId)

    val maxActiveId = state.contracts.active.maxOfOrNull { num(it.id) } ?: 0
    val maxHeroId = state.heroes.roster.maxOfOrNull { num(it.id) } ?: 0

    val nextContractIdNum = num(state.meta.ids.nextContractId)
    val nextActiveContractIdNum = num(state.meta.ids.nextActiveContractId)
    val nextHeroIdNum = num(state.meta.ids.nextHeroId)

    // --- 2) ID invariants ---
    if (nextContractIdNum <= 0) {
        add(InvariantId.IDS__NEXT_CONTRACT_ID_POSITIVE, "nextContractId=$nextContractIdNum must be > 0")
    }
    if (nextActiveContractIdNum <= 0) {
        add(InvariantId.IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE, "nextActiveContractId=$nextActiveContractIdNum must be > 0")
    }
    if (nextHeroIdNum <= 0) {
        add(InvariantId.IDS__NEXT_HERO_ID_POSITIVE, "nextHeroId=$nextHeroIdNum must be > 0")
    }

    if (nextContractIdNum <= maxContractId) {
        add(
            InvariantId.IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID,
            "nextContractId=$nextContractIdNum must be > maxContractId=$maxContractId"
        )
    }
    if (nextActiveContractIdNum <= maxActiveId) {
        add(
            InvariantId.IDS__NEXT_ACTIVE_CONTRACT_ID_GT_MAX_ACTIVE_ID,
            "nextActiveContractId=$nextActiveContractIdNum must be > maxActiveId=$maxActiveId"
        )
    }
    if (nextHeroIdNum <= maxHeroId) {
        add(
            InvariantId.IDS__NEXT_HERO_ID_GT_MAX_HERO_ID,
            "nextHeroId=$nextHeroIdNum must be > maxHeroId=$maxHeroId"
        )
    }

    // --- 3) Contracts: LOCKED board must have exactly one non-closed active referencing it ---
    val actives = state.contracts.active
    val returns = state.contracts.returns

    state.contracts.board
        .asSequence()
        .filter { it.status == BoardStatus.LOCKED }
        .sortedBy { num(it.id) }
        .forEach { board ->
            val nonClosedActiveIds = actives
                .asSequence()
                .filter { it.boardContractId == board.id }
                .filter { it.status != ActiveStatus.CLOSED }
                .map { num(it.id) }
                .sorted()
                .toList()

            if (nonClosedActiveIds.size != 1) {
                add(
                    InvariantId.CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE,
                    "boardId=${num(board.id)} expectedExactlyOneNonClosedActive but found=${nonClosedActiveIds.size}; activeIds=$nonClosedActiveIds"
                )
            }
        }

    // --- 4) Contracts: RETURN_READY active must have exactly one return packet ---
    actives
        .asSequence()
        .filter { it.status == ActiveStatus.RETURN_READY }
        .sortedBy { num(it.id) }
        .forEach { active ->
            val packets = returns.filter { it.activeContractId == active.id }
            if (packets.size != 1) {
                add(
                    InvariantId.CONTRACTS__RETURN_READY_HAS_RETURN_PACKET,
                    "activeId=${num(active.id)} is RETURN_READY but returnPacketsFound=${packets.size}"
                )
            }
        }

    // --- 5) Contracts: Return packet points to existing active (ONLY when close is required) ---
    // PoC nuance:
    // - If requiresPlayerClose=false, the reducer may auto-finalize and drop the active contract.
    //   Such packets are informational and must NOT fail referential checks.
    val activeIdNums = actives.asSequence().map { num(it.id) }.toSet()

    val invReferentialIntegrity: InvariantId? =
        runCatching { InvariantId.valueOf("INV_REFERENTIAL_INTEGRITY") }.getOrNull()

    returns
        .asSequence()
        .filter { it.requiresPlayerClose }
        .sortedWith(compareBy<ReturnPacket>({ num(it.boardContractId) }, { num(it.activeContractId) }))
        .forEach { p ->
            val aId = num(p.activeContractId)
            if (!activeIdNums.contains(aId)) {
                add(
                    InvariantId.CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE,
                    "returnPacket.activeId=$aId not found in actives; packet=${fmtReturn(p)}"
                )
                if (invReferentialIntegrity != null) {
                    add(
                        invReferentialIntegrity,
                        "return.activeId=$aId missing; return=${fmtReturn(p)}"
                    )
                }
            }
        }

    // --- 6) Contracts: Active daysRemaining must be non-negative ---
    actives
        .asSequence()
        .sortedBy { num(it.id) }
        .forEach { a ->
            if (a.daysRemaining < 0) {
                add(
                    InvariantId.CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE,
                    "activeId=${num(a.id)} daysRemaining=${a.daysRemaining} < 0"
                )
            }
        }

    // --- 7) Contracts: WIP daysRemaining must be in 1..2 ---
    actives
        .asSequence()
        .filter { it.status == ActiveStatus.WIP }
        .sortedBy { num(it.id) }
        .forEach { a ->
            if (a.daysRemaining !in 1..2) {
                add(
                    InvariantId.CONTRACTS__WIP_DAYS_REMAINING_IN_1_2,
                    "activeId=${num(a.id)} is WIP but daysRemaining=${a.daysRemaining} not in [1,2]"
                )
            }
        }

    // --- 8) Heroes invariants ---
    // Use numeric keys to avoid assuming HeroId is Int/Comparable/etc.
    val heroesByNum = state.heroes.roster.associateBy { num(it.id) }

    // (a) Active in WIP/RETURN_READY => all assigned heroes exist and are ON_MISSION
    actives
        .asSequence()
        .filter { it.status == ActiveStatus.WIP || it.status == ActiveStatus.RETURN_READY }
        .sortedBy { num(it.id) }
        .forEach { a ->
            a.heroIds.forEach { hid ->
                val heroNum = num(hid)
                val hero = heroesByNum[heroNum]
                if (hero == null || hero.status != HeroStatus.ON_MISSION) {
                    add(
                        InvariantId.HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION,
                        "activeId=${num(a.id)} heroId=$heroNum expected ON_MISSION but was ${hero?.status}"
                    )
                }
            }
        }

    // (b) Hero status=ON_MISSION must appear in exactly one WIP/RETURN_READY active
    val usage = mutableMapOf<Int, Int>()
    actives
        .asSequence()
        .filter { it.status == ActiveStatus.WIP || it.status == ActiveStatus.RETURN_READY }
        .forEach { a ->
            a.heroIds.forEach { hid ->
                val k = num(hid)
                usage[k] = (usage[k] ?: 0) + 1
            }
        }

    state.heroes.roster
        .asSequence()
        .filter { it.status == HeroStatus.ON_MISSION }
        .sortedBy { num(it.id) }
        .forEach { h ->
            val k = num(h.id)
            val c = usage[k] ?: 0
            if (c != 1) {
                add(
                    InvariantId.HEROES__ON_MISSION_IN_EXACTLY_ONE_ACTIVE_CONTRACT,
                    "heroId=$k is ON_MISSION but referencedByActives=$c"
                )
            }
        }

    // --- 9) Economy/Region/Guild invariants ---
    val money = state.economy.moneyCopper
    val reserved = state.economy.reservedCopper
    val trophies = state.economy.trophiesStock

    if (money < 0) add(InvariantId.ECONOMY__MONEY_NON_NEGATIVE, "moneyCopper=$money < 0")
    if (reserved < 0) add(InvariantId.ECONOMY__RESERVED_NON_NEGATIVE, "reservedCopper=$reserved < 0")
    if (trophies < 0) add(InvariantId.ECONOMY__TROPHIES_NON_NEGATIVE, "trophiesStock=$trophies < 0")

    val available = money - reserved
    if (available < 0) {
        add(
            InvariantId.ECONOMY__AVAILABLE_NON_NEGATIVE,
            "availableCopper=$available (money=$money,reserved=$reserved) < 0"
        )
    }

    val stability = state.region.stability
    if (stability !in 0..100) {
        add(InvariantId.REGION__STABILITY_0_100, "stability=$stability not in [0,100]")
    }

    val reputation = state.guild.reputation
    if (reputation !in 0..100) {
        add(InvariantId.GUILD__REPUTATION_0_100, "reputation=$reputation not in [0,100]")
    }

    return violations
}
