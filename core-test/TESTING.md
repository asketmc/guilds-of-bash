# Testing Guide

> **Single source of truth for test architecture, conventions, and practices.**

---

## Quick Start

```powershell
# Run all tests
.\gradlew.bat :core-test:test

# Run smoke tests only (fast PR gate)
.\gradlew.bat :core-test:test --tests "test.suites.SmokeSuite"

# Run single test class
.\gradlew.bat :core-test:test --tests "test.invariants.InvariantVerificationTest"

# Run with verbose output
.\gradlew.bat :core-test:test --info
```

**Reports:**
- HTML: `core-test/build/reports/tests/test/index.html`
- XML: `core-test/build/test-results/test/*.xml`

---

## Recent Changes

### MVP: Accept/Reject Return Closure (2026-02-02)

**Goal:** Eliminate permanent "stuck return" situations under `ProofPolicy.STRICT` by making manual return closure an explicit player decision.

**Changes:**
- Added `ReturnDecision` enum (`ACCEPT`, `REJECT`)
- Extended `CloseReturn` command with optional `decision` parameter
- Under `STRICT` policy, an explicit decision is now required
- `REJECT` always allowed (terminates lifecycle, no payment, zero trophies)
- `ACCEPT` may be denied under `STRICT` if proof is damaged or theft suspected
- Console syntax: `close <id> accept` or `close <id> reject`
- New event: `ReturnRejected` (emitted for reject decision)
- Zero RNG draws in close path (deterministic)

**Determinism:** Same seed + commands → same events, same rngDraws count.

**Test Coverage:** `ReturnClosureDecisionTest` (19 tests, P1/Smoke)
- Validation & Policy: STRICT requires explicit decision, REJECT bypasses money validation
- Lifecycle: Both ACCEPT & REJECT terminate lifecycle correctly
- Economy: REJECT releases escrow, pays no fee, awards 0 trophies
- Progression: REJECT doesn't increment counters (FAIL-like), ACCEPT does
- Events: Correct event emission (ReturnRejected vs ReturnClosed)
- Determinism: Zero RNG draws for both paths, replay stability verified
- Policy edge cases: STRICT + damaged/suspected behavior verified

---

## Project Structure

```
core-test/
├── TESTING.md                 # This file - single source of truth
├── build.gradle.kts
└── src/test/kotlin/test/
    ├── Tags.kt                # Priority & execution tag annotations
    ├── suites/                # Test orchestration (JUnit Platform Suites)
    │   └── SmokeSuite.kt
    ├── helpers/               # Shared test utilities & assertions
    │   ├── TestApi.kt
    │   ├── InvariantAssertions.kt
    │   ├── EventAssertions.kt
    │   └── ...
    └── *Test.kt               # Test classes (flat, domain-named)
```

**Test files by domain:**

| Domain      | Files                                                                                          |
|-------------|------------------------------------------------------------------------------------------------|
| Invariants  | `InvariantVerificationTest`, `LockedBoardInvariantTest`, `InvariantsAfterEachStepTest`         |
| Contracts   | `CreateContractTest`, `UpdateContractTermsTest`, `CancelContractTest`, `ContractExpiryPoCTest`, `ReturnClosureDecisionTest` |
| Economy     | `FeeEscrowTest`, `SellTrophiesTest`, `TrophyPipelineTest`                                      |
| Determinism | `GoldenReplaysTest`, `RngDrawOrderGoldenTest`, `SerializationTest`, `HashingTest`              |
| Validation  | `CommandValidationTest`                                                                        |
| Reducer     | `ReducerCriticalTest`, `GameStateInitializationTest`, `StabilityUpdatedTest`                   |
| Scenarios   | `PoCScenarioTest`, `EdgeCasesPoCTest`, `AbuseTechnicalPoCTest`, `OutcomeBranchesTest`          |
| Performance | `CorePerfLoadTest`                                                                             |

---

## Priority Tags

Tests are tagged with JUnit5 meta-annotations defined in `Tags.kt`:

| Tag      | Purpose                              | CI Execution  |
|----------|--------------------------------------|---------------|
| `@P0`    | Core/heart — app won't start         | PR, Push      |
| `@P1`    | Feature broken / critical regression | PR, Push      |
| `@P2`    | Normal correctness (non-critical)    | Push only     |
| `@P3`    | Low value / edge cases               | Push only     |
| `@Smoke` | Ultra-fast subset for PR gate        | PR            |
| `@Perf`  | Performance/load tests               | Nightly       |

**Example:**
```kotlin
@P1
@Smoke
class InvariantVerificationTest { ... }
```

---

## Test Suites

Located in `test/suites/`:

| Suite         | Description         | Command                            |
|---------------|---------------------|------------------------------------|
| `SmokeSuite`  | Fast PR gate (~10s) | `--tests "test.suites.SmokeSuite"` |

---

## Writing New Tests

### 1. Choose a Descriptive Name

Name files by what they test, not by priority:
```
{Feature}Test.kt          # e.g., FeeEscrowTest.kt
{Domain}Test.kt           # e.g., InvariantVerificationTest.kt
{Scenario}Test.kt         # e.g., GoldenReplaysTest.kt
```

### 2. Test Structure (AAA Pattern)

```kotlin
@Test
fun `descriptive name with spaces`() {
    // GIVEN
    val state = baseState(seed = 42u)
    
    // WHEN
    val (newState, events) = step(state, command, rng)
    
    // THEN
    assertNoViolations(newState)
    assertEventTypesMatch(events, listOf("ContractPosted"))
}
```

### 3. Tag Appropriately

```kotlin
@P1                    // Priority (required)
@Smoke                 // Add if test is fast and critical
class MyFeatureTest {
    // ...
}
```

### 4. Use Helpers

```kotlin
// From TestApi.kt
val state = baseState(seed)
val result = runScenario(scenario)

// From InvariantAssertions.kt
assertNoViolations(state)
assertStateValid(state)
expectViolation(seed, InvariantId.ECONOMY__MONEY_NON_NEGATIVE) { ... }

// From EventAssertions.kt
assertNoRejections(events)
assertEventTypesMatch(events, expected)
```

---

## Determinism Contract

All tests must be **deterministic** and **reproducible**:

1. **Fixed Seeds** — Use `baseState(seed)` with explicit seeds
2. **No Side Effects** — Reducer is pure (no I/O, no wall-clock)
3. **Stable RNG Order** — RNG draws occur in predictable sequence
4. **Canonical Serialization** — JSON fields ordered deterministically

**Golden Replays** verify these contracts:
- Same seeds + commands → same final state hash
- Same seeds + commands → same event sequence
- Same seeds + commands → same RNG draw count

---

## Testing Levels & Scope

This project uses a **risk-driven testing strategy**.
A test level exists only if it covers risks not addressed by lower levels.

### Implemented

**Unit & in-process integration tests (primary)**

- Deterministic tests against the core reducer (`step`)
- Cover business rules, invariants, validation, and determinism
- Multi-step scenarios are executed in-process via the same public API

If behavior is broken here, it is considered broken globally.

---

### Out of Scope (Current)

**`.jar` black-box / E2E tests**

Jar-level tests (e.g. `java -jar app.jar`) are intentionally not implemented.

**Rationale:**

- Pure, deterministic core + thin adapter
- No CLI contract, runtime config, OS-specific logic, or packaging risks
- No additional risk introduced at the artifact level

Invariant:

> Works in unit/integration tests ⇒ works in the jar  
> Broken in unit/integration tests ⇒ broken in the jar

Under these constraints, jar-level tests would only duplicate coverage without increasing confidence.

---

### When This Changes

Jar-level tests will be added if new risks appear, such as:
- stable CLI / entry-point contracts
- runtime args or environment configuration
- OS-specific behavior
- packaging or shading complexity
- external users or compatibility guarantees


## Invariants

Invariants are post-state checks verified after every command.

| Category  | Examples                               |
|-----------|----------------------------------------|
| IDs       | `nextContractId > max(inbox+board).id` |
| Contracts | Board LOCKED → active exists           |
| Heroes    | ON_MISSION → exactly 1 active          |
| Economy   | `moneyCopper ≥ 0`, `trophiesStock ≥ 0` |
| Region    | `stability ∈ [0, 100]`                 |

**Verification:** `verifyInvariants(state)` returns list of violations (empty = valid).

---

## Feature Groups (PoC Scope)

| ID    | Feature                | Key Tests                             |
|-------|------------------------|---------------------------------------|
| FG_01 | Day Progression        | `PoCScenarioTest`                     |
| FG_02 | Contract Posting       | `CreateContractTest`, `FeeEscrowTest` |
| FG_03 | Contract Taking        | `PoCScenarioTest`                     |
| FG_04 | Contract Resolution    | `TrophyPipelineTest`                  |
| FG_05 | Return Processing      | `FeeEscrowTest`                       |
| FG_06 | Trophy Sales           | `SellTrophiesTest`                    |
| FG_07 | Command Validation     | `CommandValidationTest`               |
| FG_08 | Invariant Verification | `InvariantVerificationTest`           |
| FG_09 | Fee Escrow             | `FeeEscrowTest`                       |

---

## CI/CD Integration

### PR Workflow
```yaml
- run: ./gradlew :core-test:test -Pinclude=smoke,p0,p1 -Pexclude=perf
```

### Push to Master
```yaml
- run: ./gradlew :core-test:test -Pexclude=perf
```

### Nightly
```yaml
- run: ./gradlew :core-test:test -Pinclude=perf
```

---

## Troubleshooting

### Test Flakiness
- Check for non-seeded randomness
- Check for time dependencies
- Check for shared mutable state

### Golden Hash Changed
1. Run test to capture new hash
2. Review git diff for logic changes
3. If intended: update hash, document in commit
4. If unintended: investigate RNG order or sorting changes

### Invariant Violated
1. Check `InvariantViolated` event details
2. Trace state mutation sequence
3. Verify command validation covers the case

---

## Migration Notes

**Legacy file prefixes** (`P1_001_`, etc.) are historical. Actual priority is determined by `@P0`/`@P1`/`@P2`/`@P3` annotations on the class.

**Deprecated docs** (consolidated into this file):
- `README.md` — now in TESTING.md
- `TEST_SUMMARY.md` — now in TESTING.md
- `INVARIANT_MAPPING.md` — invariant tables in TESTING.md
- `POC_MANIFEST.md` — feature groups in TESTING.md

---

*Last updated: 2026-01-29*
