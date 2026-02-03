# Testing Guide

Single source of truth for test architecture, conventions, and practices.

---

## Quick Start

```powershell
# Run PR gate tests (smoke + p0 + p1)
.\gradlew.bat :core-test:testPr

# Run smoke tag only
.\gradlew.bat :core-test:smokeTest

# Run all tests except perf and flaky
.\gradlew.bat :core-test:testAllNoPerf

# Run perf tests only
.\gradlew.bat :core-test:perfTest

# Run flaky quarantine only
.\gradlew.bat :core-test:testQuarantine

# Run a single test class
.\gradlew.bat :core-test:test --tests "test.InvariantVerificationTest"

# Run a single test method
.\gradlew.bat :core-test:test --tests "test.InvariantVerificationTest.descriptiveTestName"

# Run with verbose output
.\gradlew.bat :core-test:test --info
```

Reports:
- HTML: `core-test/build/reports/tests/test/index.html`
- XML: `core-test/build/test-results/test/*.xml`

Sources:
- Test tasks: `build-logic/src/main/kotlin/gob.core-test.gradle.kts`
- Tags: `core-test/src/test/kotlin/test/Tags.kt`
- Smoke suite class: `core-test/src/test/kotlin/test/suites/SmokeSuite.kt`

---

## Recent Changes

### Accept or reject manual return closure

Goal: eliminate stuck returns under `ProofPolicy.STRICT` by making manual return closure an explicit player decision.

Changes:
- Added `ReturnDecision` enum with `ACCEPT` and `REJECT`
- Extended `CloseReturn` with optional decision parameter
- Under `ProofPolicy.STRICT`, a decision is required
- `REJECT` terminates the lifecycle with no payout and zero trophies
- `ACCEPT` may be denied under `ProofPolicy.STRICT` if proof is damaged or theft is suspected
- Console syntax: `close <id> accept` and `close <id> reject`
- Added event `ReturnRejected`

Determinism contract: same seed and commands produce the same events and the same RNG draw count.

Test coverage:
- `ReturnClosureDecisionTest` in `core-test/src/test/kotlin/test/ReturnClosureDecisionTest.kt`

---

## Project Structure

```
core-test/
├── TESTING.md
├── TEST_REVIEW.md
├── build.gradle.kts
└── src/test/kotlin/
    ├── test/
    │   ├── Tags.kt
    │   ├── suites/
    │   │   └── SmokeSuite.kt
    │   ├── helpers/
    │   └── *Test.kt
    └── core/
        └── partial/
```

Test files by domain:

| Domain      | Files |
|-------------|-------|
| Invariants  | `InvariantVerificationTest`, `LockedBoardInvariantTest`, `InvariantsAfterEachStepTest`, `ArchiveAutoCloseInvariantTest`, `ClientDepositEscrowInvariantTest` |
| Contracts   | `CreateContractTest`, `UpdateContractTermsTest`, `CancelContractTest`, `ContractExpiryPoCTest`, `ReturnClosureDecisionTest`, `ArchiveContractTest` |
| Economy     | `FeeEscrowTest`, `SellTrophiesTest`, `TrophyPipelineTest`, `MoneyTest`, `MoneyIntegrationTest` |
| Determinism | `GoldenReplaysTest`, `RngDrawOrderGoldenTest`, `SerializationTest`, `HashingTest` |
| Validation  | `CommandValidationTest` |
| Reducer     | `ReducerCriticalTest`, `GameStateInitializationTest`, `StabilityUpdatedTest` |
| Scenarios   | `PoCScenarioTest`, `EdgeCasesPoCTest`, `AbuseTechnicalPoCTest`, `OutcomeBranchesTest`, `GoldenReplaysTest` |
| Performance | `CorePerfLoadTest` |

---

## Priority tags

Tests use JUnit5 meta-annotations defined in `core-test/src/test/kotlin/test/Tags.kt`:

| Tag      | Purpose | Execution |
|----------|---------|-----------|
| `@P0`    | Core/heart | Included in `testPr` |
| `@P1`    | Critical regression | Included in `testPr` |
| `@P2`    | Normal correctness | Excluded from `testPr` |
| `@P3`    | Low value or edge | Excluded from `testPr` |
| `@Smoke` | Fast subset | Included in `smokeTest` and `testPr` |
| `@Perf`  | Performance or load | Runs only in `perfTest` |

Tag values at runtime:
- `@P0` -> `@Tag("p0")`
- `@P1` -> `@Tag("p1")`
- `@P2` -> `@Tag("p2")`
- `@P3` -> `@Tag("p3")`
- `@Smoke` -> `@Tag("smoke")`
- `@Perf` -> `@Tag("perf")`

---

## Smoke selection

There are two ways to run smoke:

- Recommended: run by tag via Gradle task `:core-test:smokeTest`
- Suite class: `test.suites.SmokeSuite` selects packages `test`, `core`, and `console` and includes tag `smoke`

The regular Gradle `test` task excludes the JUnit Platform suite engine. Running `SmokeSuite` through `:core-test:test` may result in suite discovery errors depending on filters.

---

## Determinism contract

All tests must be deterministic and reproducible:

1. Fixed seeds via `initialState(seed)` and explicit RNG seeds
2. No wall clock time or I/O in the reducer
3. Stable RNG draw order
4. Canonical serialization and hashing

---

## Testing levels and scope

This project uses deterministic in-process unit and integration tests against the core reducer.

Jar-level black-box tests are not implemented.

Jar-level tests will be added if new risks appear:
- stable CLI or entry-point contracts
- runtime args or environment configuration
- OS-specific behavior
- packaging or shading complexity

---

## CI integration

The canonical tasks are defined in `build-logic/src/main/kotlin/gob.core-test.gradle.kts`:

- PR gate: `:core-test:testPr`
- Smoke-only: `:core-test:smokeTest`
- Full excluding perf and flaky: `:core-test:testAllNoPerf`
- Perf only: `:core-test:perfTest`
- Flaky quarantine only: `:core-test:testQuarantine`

---

## Troubleshooting

Test flakiness:
- verify no non-seeded randomness
- verify no time dependencies
- verify no shared mutable state

Golden hash change:
1. confirm reducer change is intended
2. confirm stable sorting and RNG draw order
3. update golden values only when intended
