// Split from TestHelpers.kt — locked-board fixtures and assertions
package test.helpers

import core.primitives.*
import core.state.*
import core.*
import core.rng.Rng
import core.invariants.InvariantId
import kotlin.test.*

data class LockedBoardFixture(
    val initial: GameState,
    val rng: Rng
)

fun boardContract(
    id: Long = 1L,
    postedDay: Int = 0,
    title: String = "Test",
    rank: Rank = Rank.F,
    fee: Int = 0,
    salvage: SalvagePolicy = SalvagePolicy.GUILD,
    baseDifficulty: Int = 1,
    status: BoardStatus = BoardStatus.LOCKED
): BoardContract = BoardContract(
    id = ContractId(id.toInt()),
    postedDay = postedDay,
    title = title,
    rank = rank,
    fee = fee,
    salvage = salvage,
    baseDifficulty = baseDifficulty,
    status = status
)

fun hero(
    id: Long,
    name: String = "Hero #$id",
    rank: Rank = Rank.F,
    klass: HeroClass = HeroClass.WARRIOR,
    traits: Traits = Traits(greed = 50, honesty = 50, courage = 50),
    status: HeroStatus = HeroStatus.ON_MISSION,
    historyCompleted: Int = 0
): Hero = Hero(
    id = HeroId(id.toInt()),
    name = name,
    rank = rank,
    klass = klass,
    traits = traits,
    status = status,
    historyCompleted = historyCompleted
)

fun active(
    id: Long,
    heroIds: List<Long>,
    status: ActiveStatus,
    boardContractId: Long = 1L,
    takenDay: Int = 1,
    daysRemaining: Int = 0
): ActiveContract = ActiveContract(
    id = ActiveContractId(id.toInt()),
    boardContractId = ContractId(boardContractId.toInt()),
    takenDay = takenDay,
    daysRemaining = daysRemaining,
    heroIds = heroIds.map { HeroId(it.toInt()) },
    status = status
)

fun returnPacket(
    activeId: Long,
    heroIds: List<Long>,
    boardContractId: Long = 1L,
    resolvedDay: Int = 1,
    outcome: Outcome = Outcome.SUCCESS,
    trophiesCount: Int = 1,
    trophiesQuality: Quality = Quality.OK,
    reasonTags: List<String> = emptyList(),
    requiresPlayerClose: Boolean = true,
    suspectedTheft: Boolean = false
): ReturnPacket = ReturnPacket(
    boardContractId = ContractId(boardContractId.toInt()),
    heroIds = heroIds.map { HeroId(it.toInt()) },
    activeContractId = ActiveContractId(activeId.toInt()),
    resolvedDay = resolvedDay,
    outcome = outcome,
    trophiesCount = trophiesCount,
    trophiesQuality = trophiesQuality,
    reasonTags = reasonTags,
    requiresPlayerClose = requiresPlayerClose,
    suspectedTheft = suspectedTheft
)

fun lockedBoardState(
    seed: UInt = 42u,
    boardStatus: BoardStatus = BoardStatus.LOCKED,
    actives: List<ActiveContract>,
    returns: List<ReturnPacket>,
    heroes: List<Hero>,
    economy: EconomyState = EconomyState(
        moneyCopper = 100,
        reservedCopper = 0,
        trophiesStock = 0
    )
): GameState = initialState(seed).copy(
    economy = economy,
    contracts = ContractState(
        inbox = emptyList(),
        board = listOf(boardContract(status = boardStatus)),
        active = actives,
        returns = returns
    ),
    heroes = HeroState(
        roster = heroes,
        arrivalsToday = emptyList()
    )
)

fun lockedBoardFixture(
    seed: UInt = 42u,
    rngSeed: Long = 100L,
    boardStatus: BoardStatus = BoardStatus.LOCKED,
    actives: List<ActiveContract>,
    returns: List<ReturnPacket>,
    heroes: List<Hero>,
    economy: EconomyState = EconomyState(
        moneyCopper = 100,
        reservedCopper = 0,
        trophiesStock = 0
    )
): LockedBoardFixture = LockedBoardFixture(
    initial = lockedBoardState(
        seed = seed,
        boardStatus = boardStatus,
        actives = actives,
        returns = returns,
        heroes = heroes,
        economy = economy
    ),
    rng = Rng(rngSeed)
)

fun closeReturn(state: GameState, activeContractId: Long, cmdId: Long, rng: Rng): StepResult =
    step(state, CloseReturn(activeContractId = activeContractId, cmdId = cmdId), rng)

fun boardStatusOf(state: GameState, boardContractId: Long = 1L): BoardStatus =
    state.contracts.board.first { it.id.value.toLong() == boardContractId }.status

fun assertBoardStatus(state: GameState, expected: BoardStatus, boardContractId: Long = 1L, message: String = "") {
    val actual = boardStatusOf(state, boardContractId)
    assertEquals(expected, actual, message.ifBlank { "Expected board[$boardContractId]=$expected, actual=$actual" })
}

fun activeStatusOf(state: GameState, activeContractId: Long): ActiveStatus =
    state.contracts.active.first { it.id.value.toLong() == activeContractId }.status

fun assertActiveStatusIn(state: GameState, activeContractId: Long, allowed: Set<ActiveStatus>, message: String = "") {
    val actual = activeStatusOf(state, activeContractId)
    assertTrue(actual in allowed, message.ifBlank { "Expected active[$activeContractId] in $allowed, actual=$actual" })
}

fun assertNoInvariantViolation(events: List<Event>, invariantId: InvariantId, message: String = "") {
    val v = events.filterIsInstance<InvariantViolated>().filter { it.invariantId == invariantId }
    assertTrue(v.isEmpty(), message.ifBlank { "Expected no InvariantViolated(${invariantId.code}) in events, got=$v" })
}
