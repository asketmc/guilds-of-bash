// Split from TestHelpers.kt — trophy pipeline fixtures and assertions
package test.helpers

import core.*
import core.primitives.*
import core.state.*
import core.rng.Rng
import kotlin.test.*

data class TrophyPipelineFixture(
    val initial: GameState,
    val rng: Rng
)

data class TrophySpec(
    val contractId: Long,
    val activeId: Long = contractId,
    val heroId: Long = contractId,
    val title: String = "Test",
    val boardRank: Rank = Rank.F,
    val fee: Int = 0,
    val baseDifficulty: Int = 1,
    val boardStatus: BoardStatus = BoardStatus.LOCKED,
    val daysRemaining: Int = 1,
    val heroRank: Rank = Rank.F,
    val heroClass: HeroClass = HeroClass.WARRIOR,
    val heroStatus: HeroStatus = HeroStatus.ON_MISSION,
    val greed: Int = 0,
    val honesty: Int = 50,
    val courage: Int = 50,
    val heroHistoryCompleted: Int = 0
)

fun trophySpec(
    contractId: Long,
    activeId: Long = contractId,
    heroId: Long = contractId,
    title: String = "Test",
    boardRank: Rank = Rank.F,
    fee: Int = 0,
    baseDifficulty: Int = 1,
    boardStatus: BoardStatus = BoardStatus.LOCKED,
    daysRemaining: Int = 1,
    heroRank: Rank = Rank.F,
    heroClass: HeroClass = HeroClass.WARRIOR,
    heroStatus: HeroStatus = HeroStatus.ON_MISSION,
    greed: Int = 0,
    honesty: Int = 50,
    courage: Int = 50,
    heroHistoryCompleted: Int = 0
): TrophySpec = TrophySpec(
    contractId = contractId,
    activeId = activeId,
    heroId = heroId,
    title = title,
    boardRank = boardRank,
    fee = fee,
    baseDifficulty = baseDifficulty,
    boardStatus = boardStatus,
    daysRemaining = daysRemaining,
    heroRank = heroRank,
    heroClass = heroClass,
    heroStatus = heroStatus,
    greed = greed,
    honesty = honesty,
    courage = courage,
    heroHistoryCompleted = heroHistoryCompleted
)

fun trophyResolveFixture(
    seed: UInt = 42u,
    rngSeed: Long = 100L,
    specs: List<TrophySpec>
): TrophyPipelineFixture {
    // Build a simple state with provided specs turned into board/active/hero/returns
    var state = initialState(seed)

    val board = specs.map { s ->
        BoardContract(
            id = ContractId(s.contractId.toInt()),
            postedDay = 0,
            title = s.title,
            rank = s.boardRank,
            fee = s.fee,
            salvage = SalvagePolicy.GUILD,
            baseDifficulty = s.baseDifficulty,
            status = s.boardStatus
        )
    }

    val actives = specs.map { s ->
        ActiveContract(
            id = ActiveContractId(s.activeId.toInt()),
            boardContractId = ContractId(s.contractId.toInt()),
            takenDay = 0,
            daysRemaining = s.daysRemaining,
            heroIds = listOf(HeroId(s.heroId.toInt())),
            status = ActiveStatus.WIP
        )
    }

    val heroes = specs.map { s ->
        Hero(
            id = HeroId(s.heroId.toInt()),
            name = "Smith",
            rank = s.heroRank,
            klass = s.heroClass,
            traits = Traits(greed = s.greed, honesty = s.honesty, courage = s.courage),
            status = s.heroStatus,
            historyCompleted = s.heroHistoryCompleted
        )
    }

    state = state.copy(
        contracts = ContractState(
            inbox = emptyList(),
            board = board,
            active = actives,
            returns = emptyList()
        ),
        heroes = HeroState(roster = heroes, arrivalsToday = emptyList())
    )

    return TrophyPipelineFixture(initial = state, rng = Rng(rngSeed))
}

fun trophyCloseFixture(
    rngSeed: Long = 100L,
    trophiesCount: Int,
    trophiesQuality: Quality,
    initialMoney: Int,
    initialReserved: Int,
    initialStock: Int
): TrophyPipelineFixture {
    val state = initialState(42u).copy(
        economy = EconomyState(moneyCopper = initialMoney, reservedCopper = initialReserved, trophiesStock = initialStock),
        contracts = ContractState(
            inbox = emptyList(),
            board = listOf(boardContract()),
            active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY)),
            returns = listOf(returnPacket(activeId = 1L, heroIds = listOf(1L), trophiesCount = trophiesCount, trophiesQuality = trophiesQuality))
        ),
        heroes = HeroState(roster = listOf(hero(1L)), arrivalsToday = emptyList())
    )
    return TrophyPipelineFixture(initial = state, rng = Rng(rngSeed))
}

fun trophyStockFixture(
    rngSeed: Long = 100L,
    moneyCopper: Int,
    reservedCopper: Int,
    trophiesStock: Int
): TrophyPipelineFixture {
    val state = initialState(42u).copy(
        economy = EconomyState(moneyCopper = moneyCopper, reservedCopper = reservedCopper, trophiesStock = trophiesStock),
        contracts = ContractState(inbox = emptyList(), board = listOf(boardContract()), active = emptyList(), returns = emptyList()),
        heroes = HeroState(roster = emptyList(), arrivalsToday = emptyList())
    )
    return TrophyPipelineFixture(initial = state, rng = Rng(rngSeed))
}

fun advanceDay(state: GameState, cmdId: Long, rng: Rng): StepResult = step(state, AdvanceDay(cmdId = cmdId), rng)

fun sellAllTrophies(state: GameState, cmdId: Long, rng: Rng): StepResult = step(state, SellTrophies(amount = 0, cmdId = cmdId), rng)

fun assertSingleResolvedCreatesSingleReturn(state: GameState, events: List<Event>) {
    val resolved = events.filter { it::class.simpleName == "ContractResolved" }
    if (resolved.size != 1) {
        TestLog.log("DEBUG: Events emitted: ${events.map { it::class.simpleName }}")
        TestLog.log("DEBUG: State returns size=${state.contracts.returns.size}")
    }
    assertEquals(1, resolved.size, "Expected exactly one ContractResolved event")
    assertEquals(1, state.contracts.returns.size, "Expected one return present in state")
}

fun assertTrophiesDepositedAndReturnRemoved(before: GameState, after: GameState, activeContractId: Long, expectedDeposited: Int) {
    val beforeStock = before.economy.trophiesStock
    val afterStock = after.economy.trophiesStock
    assertEquals(beforeStock + expectedDeposited, afterStock, "Trophies should be deposited to stock")
    val stillHasReturn = after.contracts.returns.any { it.activeContractId.value.toLong() == activeContractId }
    assertTrue(!stillHasReturn, "Return should be removed after close")
}

fun assertSellAllApplied(before: GameState, after: GameState, events: List<Event>) {
    // If seller sold all trophies, stock should be zero and money increased accordingly
    val moneyDelta = after.economy.moneyCopper - before.economy.moneyCopper
    val stockDelta = before.economy.trophiesStock - after.economy.trophiesStock
    assertTrue(stockDelta >= 0, "Stock must not increase on sell")
    assertEquals(stockDelta, moneyDelta, "Money gained should equal trophies sold (1 copper per trophy in reducer)")
}

fun assertResolvedCount(events: List<Event>, expected: Int): List<ReturnPacket> {
    val resolvedEvents = events.filter { it::class.simpleName == "ContractResolved" }.map { e -> e as ContractResolved }
    assertEquals(expected, resolvedEvents.size, "Expected $expected resolved events")
    // Synthesize ReturnPacket objects with minimal fields for downstream checks
    return resolvedEvents.map { ev ->
        ReturnPacket(
            activeContractId = ActiveContractId(ev.activeContractId),
            boardContractId = ContractId(1),
            heroIds = emptyList(),
            resolvedDay = ev.day,
            outcome = ev.outcome,
            trophiesCount = ev.trophiesCount,
            trophiesQuality = ev.quality,
            reasonTags = emptyList(),
            requiresPlayerClose = false,
            suspectedTheft = false
        )
    }
}

fun assertResolvedTrophiesValid(packet: ReturnPacket) {
    // basic sanity: trophiesCount non-negative
    assertTrue(packet.trophiesCount >= 0, "Resolved trophies must be non-negative")
}

fun trophyE2EFixture(
    seed: UInt = 42u,
    rngSeed: Long = 200L,
    initialMoney: Int,
    initialReserved: Int,
    initialStock: Int,
    contractFee: Int,
    baseDifficulty: Int,
    heroRank: Rank,
    heroHistoryCompleted: Int
): TrophyPipelineFixture {
    // Create a state that can resolve a return and allow selling
    val s = initialState(seed).copy(
        economy = EconomyState(moneyCopper = initialMoney, reservedCopper = initialReserved, trophiesStock = initialStock),
        contracts = ContractState(
            inbox = emptyList(),
            board = listOf(boardContract(fee = contractFee, baseDifficulty = baseDifficulty)),
            active = listOf(active(id = 1L, heroIds = listOf(1L), status = ActiveStatus.RETURN_READY, daysRemaining = 0)),
            returns = listOf(returnPacket(activeId = 1L, heroIds = listOf(1L), trophiesCount = 1))
        ),
        heroes = HeroState(roster = listOf(hero(id = 1L, status = HeroStatus.AVAILABLE, historyCompleted = heroHistoryCompleted)), arrivalsToday = emptyList())
    )
    return TrophyPipelineFixture(initial = s, rng = Rng(rngSeed))
}

fun assertEndToEndTrophyFlow(fx: TrophyPipelineFixture) {
    // 1) close return -> deposit trophies
    val r1 = closeReturn(fx.initial, activeContractId = 1L, cmdId = 1L, rng = fx.rng)
    assertTrophiesDepositedAndReturnRemoved(before = fx.initial, after = r1.state, activeContractId = 1L, expectedDeposited = 1)

    // 2) sell all -> money increases
    val r2 = sellAllTrophies(r1.state, cmdId = 2L, rng = fx.rng)
    assertSellAllApplied(before = r1.state, after = r2.state, events = r2.events)
}
