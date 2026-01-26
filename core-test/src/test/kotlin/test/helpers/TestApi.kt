// Split from TestHelpers.kt — shared test API: scenarios, sessions, determinism helpers
package test.helpers

import core.*
import core.hash.hashEvents
import core.hash.hashState
import core.rng.Rng
import core.state.GameState
import core.state.initialState
import core.state.*
import core.primitives.*
import kotlin.test.*

// Convenience extension properties to access nested GameState fields
val GameState.inbox get() = contracts.inbox
val GameState.board get() = contracts.board
val GameState.active get() = contracts.active
val GameState.returns get() = contracts.returns
val GameState.nextContractId get() = meta.ids.nextContractId

// Scenario runner (golden replays)

data class Scenario(
    val scenarioId: String,
    val stateSeed: UInt,
    val rngSeed: Long,
    val commands: List<Command>,
    val expectedMainEventTypes: List<String>? = null,
    val description: String = ""
)

data class ScenarioResult(
    val finalState: GameState,
    val allEvents: List<Event>,
    val stepResults: List<StepResult>,
    val rngDraws: Long
)

fun runScenario(scenario: Scenario): ScenarioResult {
    var state = initialState(scenario.stateSeed)
    val rng = Rng(scenario.rngSeed)

    val stepResults = mutableListOf<StepResult>()
    val allEvents = mutableListOf<Event>()

    for (cmd in scenario.commands) {
        val result = step(state, cmd, rng)
        stepResults += result
        allEvents += result.events
        state = result.state
    }

    return ScenarioResult(
        finalState = state,
        allEvents = allEvents,
        stepResults = stepResults,
        rngDraws = rng.draws
    )
}

// Replay determinism

fun assertReplayDeterminism(scenario: Scenario, message: String = "") {
    val r1 = runScenario(scenario)
    val r2 = runScenario(scenario)

    val stateHash1 = hashState(r1.finalState)
    val stateHash2 = hashState(r2.finalState)
    assertEquals(stateHash1, stateHash2, message.ifBlank { "State hashes must match: $stateHash1 vs $stateHash2" })

    val eventsHash1 = hashEvents(r1.allEvents)
    val eventsHash2 = hashEvents(r2.allEvents)
    assertEquals(eventsHash1, eventsHash2, message.ifBlank { "Event hashes must match: $eventsHash1 vs $eventsHash2" })

    assertEquals(r1.rngDraws, r2.rngDraws, message.ifBlank { "RNG draws must match: ${r1.rngDraws} vs ${r2.rngDraws}" })
}

// Tiny state-machine session helpers

data class TestSession(
    var state: GameState,
    val rng: Rng,
    var cmdId: Long = 1L
)

fun session(
    stateSeed: UInt = 42u,
    rngSeed: Long = 100L,
    cmdIdStart: Long = 1L
): TestSession = TestSession(
    state = initialState(stateSeed),
    rng = Rng(rngSeed),
    cmdId = cmdIdStart
)

/** Run a command, mutate session state, return StepResult for assertions. */
fun TestSession.run(cmd: Command): StepResult {
    val res = step(state, cmd, rng)
    state = res.state
    return res
}

fun TestSession.advanceDay(): StepResult = run(AdvanceDay(cmdId = cmdId++))

fun TestSession.postContractFromInbox(
    inboxIndex: Int = 0,
    fee: Int,
    salvage: SalvagePolicy
): StepResult {
    val inboxItem = state.contracts.inbox.getOrNull(inboxIndex)
        ?: fail("Precondition failed: inbox[$inboxIndex] is missing (size=${state.contracts.inbox.size})")
    val inboxId = inboxItem.id.value.toLong()
    return run(PostContract(inboxId = inboxId, fee = fee, salvage = salvage, cmdId = cmdId++))
}

fun TestSession.closeReturn(activeContractId: Long): StepResult =
    run(CloseReturn(activeContractId = activeContractId, cmdId = cmdId++))

fun TestSession.sellTrophies(amount: Int): StepResult =
    run(SellTrophies(amount = amount, cmdId = cmdId++))

/** Determinism for a single step: same input state + same command + same rng seed => same (hashed) outputs. */
fun assertStepDeterministic(
    state: GameState,
    cmd: Command,
    rngSeed: Long,
    message: String = ""
) {
    val r1 = step(state, cmd, Rng(rngSeed))
    val r2 = step(state, cmd, Rng(rngSeed))

    val hS1 = hashState(r1.state)
    val hS2 = hashState(r2.state)
    assertEquals(hS1, hS2, message.ifBlank { "Determinism violated: state hashes differ ($hS1 vs $hS2)" })

    val hE1 = hashEvents(r1.events)
    val hE2 = hashEvents(r2.events)
    assertEquals(hE1, hE2, message.ifBlank { "Determinism violated: event hashes differ ($hE1 vs $hE2)" })
}

fun requireReturnRequiringClose(state: GameState, message: String = ""): ReturnPacket {
    val list = state.contracts.returns.filter { it.requiresPlayerClose }
    assertTrue(
        list.isNotEmpty(),
        message.ifBlank { "Precondition failed: expected at least one return requiring player close" }
    )
    return list.first()
}

fun requireTrophyStockPositive(state: GameState, message: String = ""): Int {
    val stock = state.economy.trophiesStock
    assertTrue(stock > 0, message.ifBlank { "Precondition failed: expected trophiesStock>0, actual=$stock" })
    return stock
}

// Economy helpers (moved from original TestHelpers)
fun assertReservedCopper(state: GameState, expected: Int, message: String = "") {
    val actual = state.economy.reservedCopper
    assertEquals(expected, actual, message.ifBlank { "Expected reservedCopper=$expected, actual=$actual" })
}

fun assertAvailableCopper(state: GameState, expected: Int, message: String = "") {
    val actual = state.economy.moneyCopper - state.economy.reservedCopper
    assertEquals(expected, actual, message.ifBlank { "Expected available money=$expected, actual=$actual" })
}
