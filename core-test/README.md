core-test — P1 unit tests

Purpose
-------
This folder contains the project's highest-priority unit tests (filename prefix `P1_`). These tests exercise critical business flows (contracts, trophies, fees) and are intended to be stable, deterministic, and runnable locally by developers.

What "P1" means
----------------
- P1: Critical unit tests. Failures indicate regressions for production-critical logic and must be fixed before releases.

Guidelines
----------
- Tests use seeded RNG and `initialState(seed)` for determinism.
- Tests should avoid external services and be in-memory only.
- When reading tests, look for `// GIVEN`, `// WHEN`, `// THEN` markers — they indicate Arrange/Act/Assert sections.

Running tests
-------------
Run from the project root using Gradle (Windows PowerShell):

```powershell
./gradlew :core-test:test
```

Where test reports land
----------------------
- HTML report: `core-test/build/reports/tests/test/index.html`
- JUnit XML: `core-test/build/test-results/test/`

---

## Golden Replay Contract

### Overview

**Golden Replays (GR)** are deterministic end-to-end scenarios that serve as regression baselines. They validate:
- **Reproducibility**: Same seeds + commands → same final state and events
- **Stability**: State/event hashes remain constant across refactorings
- **Correctness**: No invariant violations on happy paths

### Golden Replay Tests

Located in: `P1_015_GoldenReplaysTest.kt`

- **GR1**: Happy path (full contract lifecycle: post → take → resolve → close → sell)
- **GR2**: Rejection scenarios (validation failures, not-found errors)
- **GR3**: Boundary cases (escrow limits, insufficient money)

### Contracts

#### 1. Deterministic Execution

**Contract**: For fixed `stateSeed`, `rngSeed`, and command sequence:
- Final state hash must be identical across runs
- Event sequence and hashes must be identical
- RNG draw count must be identical

**Enforced by**: `P1_015_GoldenReplaysTest::golden replay hashes are stable across runs`

#### 2. Invariant Preservation

**Contract**: After **every** step in a golden replay:
- `verifyInvariants(state)` must return empty list
- No `InvariantViolated` events on happy paths
- Rejected commands must leave state valid

**Enforced by**: `P1_020_InvariantsAfterEachStepTest`

#### 3. RNG Draw Order Stability

**Contract**: RNG draw count must not change unexpectedly.
- If `rng.draws` changes for same scenario, investigate logic changes
- RNG draws are documented as golden baselines

**Enforced by**: `P1_019_RngDrawOrderGoldenTest::RNG draw order regression detection for GR1 scenario`

#### 4. Serialization Round-Trip

**Contract**: Serialized states must be deserializable without data loss.
- `deserialize(serialize(state))` must equal `state` (structurally)
- Hashes must be stable across serialization round-trips

**Enforced by**: `P1_011_SerializationTest`

### Test Helpers

Located in: `TestHelpers.kt`

**Key Functions**:
- `runScenario(scenario)` — Execute command sequence and return result
- `assertNoInvariantViolations(events)` — Verify no invariant violations
- `assertNoRejections(events)` — Verify no command rejections
- `assertEventTypesMatch(events, expected)` — Verify event sequence

**Scenario Data Class**:
```kotlin
data class Scenario(
    val scenarioId: String,
    val stateSeed: UInt,
    val rngSeed: Long,
    val commands: List<Command>,
    val expectedMainEventTypes: List<String>? = null
)
```

### Reproducibility Requirements

To ensure golden replay stability:

1. **Fixed Seeds**: Always use deterministic seeds (no `Random()`)
2. **No Side Effects**: Reducer must be pure (no I/O, no wall-clock time)
3. **Canonical Serialization**: JSON fields must be ordered deterministically
4. **RNG Order**: RNG draws must occur in stable order (same sequence for same logic)
5. **Invariant Checks**: `verifyInvariants()` called after every command

### Breaking Golden Replays

**When golden hashes change, investigate**:
- ✅ **Intended**: Schema version bumped, new fields added
- ✅ **Intended**: Logic fix that changes outcomes
- ⚠️ **Unintended**: Refactoring changed RNG order
- ⚠️ **Unintended**: Sorting/ordering changed
- ❌ **Bug**: Invariant violated, state corrupted

**Process**:
1. Run test to capture new hashes
2. Review git diff for logic changes
3. Update golden hashes if change is intended
4. Document change in commit message

---

## Documentation Files

- **`POC_MANIFEST.md`**: Feature group decomposition (commands, events, state)
- **`INVARIANT_MAPPING.md`**: Invariant IDs → code → test coverage mapping
- **`core/SERIALIZATION_STABILITY.md`**: Schema evolution and backward compatibility rules

---

## Test Naming Convention

- `P1_NNN_DescriptionTest.kt` — P1 critical tests (NNN = 001-020+)
- Tests are numbered for organization, not execution order
- New P1 tests should increment the highest existing number
