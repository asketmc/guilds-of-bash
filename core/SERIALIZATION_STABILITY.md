# Serialization Stability Contract

**Purpose**: Preserve deterministic reproducibility and backward compatibility of persisted `GameState` across refactors and schema evolution.

**Scope**: `serialize(state)` / `deserialize(payload)` for persisted saves and for test-enforced golden replays.

**Version**: 1.1  
**Last Updated**: 2026-02-03

---

## TL;DR

### Guarantees
- **Determinism**: For fixed `(initial state, command sequence, RNG inputs)`, produced states/events are reproducible.
- **Golden replay stability**: `hash(serialize(finalState))` is stable across code changes **unless** a schema break is explicitly declared via `saveVersion` bump.
- **Backward compatibility**: Old saves (supported versions) must deserialize on newer code.

### Rules of change
- **Add field**: allowed without version bump **only if** default is semantically correct for old saves.
- **Rename field**: treat as **add+deprecate**, keep both for N releases, then remove with a version bump.
- **Remove field**: deprecate first; removal is a breaking change unless safely ignored with compatibility window.
- **Change type/semantics**: **requires** version bump + migration.

### Enforced by tests
- Round-trip identity, hash stability, version validation, defaults behavior, golden replay scenarios.
- See: `core-test/src/test/kotlin/test/SerializationTest.kt`, `core-test/src/test/kotlin/test/GoldenReplaysTest.kt`

---

## Definitions

- **DTO**: serialized representation (e.g., `GameStateDto` tree).
- **Domain**: in-memory state objects used by reducer.
- **saveVersion**: integer schema version embedded in serialized payload.
- **Canonical serialization**: deterministic field ordering/config such that the same logical state yields the same serialized bytes/text.

---

## Supported versions policy

- **SUPPORTED_SAVE_VERSION**: `1`
- **Supported inputs**: `saveVersion == 1`
- **Unsupported**:
  - `saveVersion < 1`: reject with explicit error
  - `saveVersion > SUPPORTED_SAVE_VERSION`: reject with explicit error

**Requirement**: error messages for unsupported versions must identify the seen version and supported range.

---

## Canonical serialization requirements

**Serializer MUST**:
- Produce deterministic output for the same DTO (stable ordering, stable formatting).
- Avoid nondeterministic sources (maps with unstable iteration order unless canonicalized).

**Deserializer MUST**:
- Accept missing fields when DTO defaults make it safe (see Defaults Policy).
- Reject incompatible versions early and clearly.

<details>
  <summary><strong>Canonical JSON expectations</strong></summary>

- Field order is deterministic.
- No platform-dependent formatting.
- Null-handling is explicitly configured and treated as part of the contract.

</details>

---

## Golden replay stability

**Contract**: For a fixed seed and command sequence, `hash(serialize(finalState))` MUST remain identical across code versions **unless**:
- `saveVersion` is bumped, or
- an explicitly documented breaking change is declared and accompanied by migration/baseline update, or
- a new field is introduced where default affects prior final states (this is a breaking semantic change and should be treated as such).

**Enforcement**:
- Golden replay tests produce final states and compare their hashes to stored baselines.
- Any hash change without an allowed reason is a regression.

---

## Defaults policy (backward compatibility)

### DTO defaults
- Any newly added DTO field intended to be backward compatible MUST have an explicit default value in the DTO.
- Defaults MUST be semantically valid for old saves (not merely “compiles”).

### Domain defaults
- Domain initialization MUST NOT silently diverge from DTO defaults.
- Mapping `fromDto()` MUST result in stable domain state for old payloads.

<details>
  <summary><strong>Example: add field (backward compatible)</strong></summary>

```kotlin
// Before (saveVersion=1)
data class EconomyStateDto(
    val moneyCopper: Int,
    val trophiesStock: Int
)

// After (still saveVersion=1 if 0 is semantically valid)
data class EconomyStateDto(
    val moneyCopper: Int,
    val trophiesStock: Int,
    val reservedCopper: Int = 0
)
````

</details>

---

## Evolution recipes

### Add a field (non-breaking)

**Allowed when**: default is semantically correct for old saves.

**Steps**:

1. Add field to DTO with explicit default.
2. Add field to domain model (if needed).
3. Update `toDto()` / `fromDto()`.
4. Extend tests: deserialize old JSON without the field, assert resulting domain value.
5. Confirm golden replay hashes remain stable.

### Rename a field (two-phase)

**Phase 1 (compatible)**:

* Add new field (with default / fallback).
* Keep old field in DTO.
* `fromDto()` reads new first, falls back to old.
* `toDto()` writes both (or writes new + preserves old if required).

**Phase 2 (breaking)**:

* After N releases, bump `saveVersion` and remove old field.
* Add migration if required.

### Remove a field (deprecate then break)

* Mark DTO field `@Deprecated`.
* `fromDto()` ignores it.
* Keep field for N releases.
* Removing it requires `saveVersion` bump (breaking) unless old payloads are still accepted via migration.

### Change type or meaning (breaking)

* MUST bump `saveVersion`.
* MUST implement migration.
* MUST add tests for old version payloads and migration correctness.
* MUST update golden baselines in a controlled change.

<details>
  <summary><strong>Migration skeleton (breaking change)</strong></summary>

```kotlin
fun deserialize(jsonString: String): GameState {
    val dto = json.decodeFromString<GameStateDto>(jsonString)

    return when (dto.meta.saveVersion) {
        1 -> fromDto(dto)
        // 2 -> fromDto(migrateV1toV2(dto))
        else -> error("Unsupported saveVersion: ${dto.meta.saveVersion}")
    }
}
```

</details>

---

## Test requirements

* **Round-trip identity**: `serialize(deserialize(x))` is stable for supported versions.
* **Hash stability**: same logical state yields same hash across runs.
* **Version validation**: unsupported versions rejected with clear error.
* **Defaults behavior**: payload missing optional fields is accepted and produces expected values.
* **Golden replays**: baselines updated only as part of explicit version bump/breaking change workflow.

---

## Baseline update policy

Changing golden baselines is allowed **only when**:

* `saveVersion` bump is performed, **or**
* a documented breaking semantic change is intentionally introduced.

**Required alongside baseline update**:

* Document the reason (commit message or doc note).
* Add/adjust migrations if backward compatibility is promised.
* Ensure tests cover old-version payloads.

---

## Non-goals

* Performance tuning / compression is not part of this contract.
* UI/adapter persistence formats are out of scope unless they serialize `GameState`.
* “Best effort” compatibility is insufficient: either supported (tested) or rejected (explicit).

---

## References

* Serialization implementation (canonical config)
* DTO definitions
* Round-trip / stability tests
* Golden replay tests and baseline storage

---

## Verified repo locations and contract surface

Serialization implementation:
- `core/src/main/kotlin/core/serde/CanonicalJson.kt`

DTO definitions:
- `core/src/main/kotlin/core/serde/StateDto.kt`

Hash inputs and algorithms:
- State hash: `core/src/main/kotlin/core/hash/Hashing.kt` uses SHA-256 over UTF-8 bytes of `core.serde.serialize(state)`
- Event hash: `core/src/main/kotlin/core/hash/Hashing.kt` uses SHA-256 over UTF-8 bytes of `core.serde.serializeEvents(events)`

Tests enforcing stability:
- Events serialization: `core-test/src/test/kotlin/test/SerializationTest.kt`
- Replay determinism: `core-test/src/test/kotlin/test/GoldenReplaysTest.kt`

Golden baselines:
- There are no external golden baseline files. Determinism is asserted in code.

Canonicalization knobs:
- JSON config is defined in `core/src/main/kotlin/core/serde/CanonicalJson.kt` as:
  - `encodeDefaults = true`
  - `explicitNulls = false`
  - `prettyPrint = false`

Versioning:
- Supported save version constant: `SUPPORTED_SAVE_VERSION = 1` in `core/src/main/kotlin/core/serde/CanonicalJson.kt`
- `deserialize` requires `dto.meta.saveVersion == SUPPORTED_SAVE_VERSION` and throws `IllegalArgumentException` with message `Unsupported saveVersion: <seen>`

Task selection:
- PR gate and tag filters are defined in `build-logic/src/main/kotlin/gob.core-test.gradle.kts`
