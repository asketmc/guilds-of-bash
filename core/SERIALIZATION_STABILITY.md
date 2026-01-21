# Serialization Stability Contract

**Purpose**: Document rules for maintaining backward compatibility when evolving GameState schema.

**Version**: 1.0
**Last Updated**: 2026-01-21

---

## Overview

The PoC requires **deterministic reproducibility** of game states across:
- Code refactorings
- Schema evolution (adding fields)
- Serialization format changes

This document defines the stability contract for `serialize(state)` / `deserialize(json)` functions.

---

## Core Principles

### 1. **Golden Replay Stability**

**Contract**: For fixed seeds and command sequence, `serialize(finalState)` hash must be identical across code versions **unless**:
- Schema version is bumped (`saveVersion`)
- New fields are added with documented defaults
- Explicit breaking change is announced

**Enforcement**: `P1_011_SerializationTest.kt` validates round-trip and hash stability.

### 2. **Immutable History**

**Contract**: Saved states from older schema versions must be deserializable with newer code.

**Mechanism**:
- `saveVersion` field tracks schema version
- `deserialize()` validates version and applies migrations
- Unsupported versions throw clear error messages

---

## Schema Evolution Rules

### Adding a Field

#### Rule: **New fields must have non-nullable defaults in DTO**

**Example**:
```kotlin
// Before (saveVersion=1)
data class EconomyStateDto(
    val moneyCopper: Int,
    val trophiesStock: Int
)

// After (saveVersion=2)
data class EconomyStateDto(
    val moneyCopper: Int,
    val trophiesStock: Int,
    val reservedCopper: Int = 0  // DEFAULT for old saves
)
```

**Rationale**: Old JSON without `reservedCopper` deserializes successfully with default `0`.

#### Process:
1. Add field to DTO with default value
2. Add field to domain model
3. Update `toDto()` / `fromDto()` mappings
4. **Do NOT bump `saveVersion`** if default is semantically valid for old saves
5. If default is NOT valid (breaking change), bump `saveVersion` and add migration logic

---

### Removing a Field

#### Rule: **Deprecated fields remain in DTO for N versions**

**Process**:
1. Mark field `@Deprecated` in DTO
2. Ignore field in `fromDto()` (don't populate domain model)
3. Keep in `toDto()` for backward compatibility (emit with default/dummy value)
4. After N releases (e.g., 3 releases), bump `saveVersion` and remove field

**Rationale**: Gradual deprecation prevents breaking old saves immediately.

---

### Renaming a Field

#### Rule: **Rename is ADD + REMOVE (2-step process)**

**Process**:
1. **Phase 1**: Add new field with new name, keep old field
   - `toDto()` populates both old and new fields
   - `fromDto()` reads new field first, falls back to old field
2. **Phase 2**: Remove old field (after N releases)
   - Bump `saveVersion`
   - Remove old field from DTO

---

### Changing Field Type

#### Rule: **Type changes require schema version bump**

**Process**:
1. Bump `saveVersion`
2. Add migration logic in `deserialize()`:
   ```kotlin
   when (dto.meta.saveVersion) {
       1 -> migrateV1toV2(dto)
       2 -> dto  // Current version
       else -> error("Unsupported saveVersion")
   }
   ```
3. Document migration in this file

---

## Save Version Policy

### Current Version: `1`

**Supported Versions**:
- `saveVersion=1`: Initial PoC schema (current)

**Unsupported Versions**:
- `saveVersion=0`: Pre-release (not supported)
- `saveVersion>1`: Future versions (reject with clear error)

### Version Bump Criteria

**Bump `saveVersion` when**:
- Breaking schema change (field type changed)
- Field removed without backward compatibility
- Semantic change (e.g., `money` changes from copper to silver)

**Do NOT bump `saveVersion` when**:
- Adding field with valid default
- Renaming field (phase 1 — still backward compatible)
- Refactoring code without schema change

---

## Field Defaults Documentation

### Current Schema (saveVersion=1)

| Field | Type | Default (if missing in JSON) | Rationale |
|-------|------|------------------------------|-----------|
| `meta.taxDueDay` | `Int` | `7` | First tax due on day 7 |
| `meta.taxAmountDue` | `Int` | `0` | No tax initially |
| `meta.taxPenalty` | `Int` | `0` | No penalty initially |
| `meta.taxMissedCount` | `Int` | `0` | No missed taxes initially |
| `economy.reservedCopper` | `Int` | `0` | No escrow initially |
| `guild.completedContractsTotal` | `Int` | `0` | No contracts completed initially |
| `guild.contractsForNextRank` | `Int` | `5` | Default rank-up threshold |

**Note**: These defaults are **implicit** in `initialState()` and **explicit** in DTO deserialization.

---

## Testing Strategy

### Automated Tests (P1_011_SerializationTest.kt)

1. **Round-trip test**: `serialize(state)` → `deserialize()` → `serialize()` produces identical JSON
2. **Hash stability test**: Same state produces same hash across runs
3. **Version validation test**: Reject unsupported `saveVersion` with clear error
4. **Default field test**: Deserialize JSON missing optional fields (with defaults)

### Regression Detection

**Golden State Snapshots**:
- `P1_015_GoldenReplaysTest.kt` produces golden states
- Serialize and hash these states
- Store hashes as regression baselines
- Fail test if hash changes without version bump

---

## Migration Examples

### Example 1: Add `reservedCopper` field (backward compatible)

**Scenario**: PoC adds fee escrow. Old saves don't have `reservedCopper`.

**Solution**:
```kotlin
// DTO (no version bump)
data class EconomyStateDto(
    val moneyCopper: Int,
    val trophiesStock: Int,
    val reservedCopper: Int = 0  // Default for old saves
)

// fromDto
fun fromDto(dto: EconomyStateDto): EconomyState {
    return EconomyState(
        moneyCopper = dto.moneyCopper,
        trophiesStock = dto.trophiesStock,
        reservedCopper = dto.reservedCopper  // Uses default 0 if missing
    )
}
```

**Testing**: Deserialize old JSON (without `reservedCopper`) and verify `reservedCopper=0`.

---

### Example 2: Change `money` from `Int` to `Long` (breaking)

**Scenario**: Need to support money > 2B (Int.MAX_VALUE).

**Solution**:
```kotlin
// Bump saveVersion to 2
private const val SUPPORTED_SAVE_VERSION = 2

// Add migration
fun deserialize(jsonString: String): GameState {
    val dto = json.decodeFromString<GameStateDto>(jsonString)

    return when (dto.meta.saveVersion) {
        1 -> {
            // Migrate v1 -> v2: Convert Int money to Long
            val migratedDto = dto.copy(
                economy = dto.economy.copy(
                    moneyCopper = dto.economy.moneyCopper.toLong()
                )
            )
            fromDto(migratedDto)
        }
        2 -> fromDto(dto)  // Current version
        else -> error("Unsupported saveVersion: ${dto.meta.saveVersion}")
    }
}
```

---

## Invariants

### Serialization Invariants (enforced by tests)

1. **Round-trip identity**: `deserialize(serialize(state)) == state` (structurally)
2. **Hash stability**: `hash(serialize(state))` is deterministic for same state
3. **Version validation**: `deserialize()` rejects unknown `saveVersion`
4. **Canonical ordering**: JSON fields are ordered (deterministic serialization)
5. **No null fields**: `explicitNulls = false` in JSON config (omit nulls for compactness)

---

## Future Considerations

### Post-PoC Enhancements

- **Compression**: gzip serialized JSON for storage (backward compatible wrapper)
- **Binary format**: Protobuf or MessagePack (requires version 2.0)
- **Snapshot diffing**: Store deltas instead of full snapshots (optimization)
- **Checksum field**: Add `checksum` to `MetaState` for tamper detection

---

## Summary

**DO**:
- Add fields with defaults (no version bump)
- Document defaults in this file
- Test backward compatibility with old JSON
- Bump version for breaking changes
- Write migration code for version bumps

**DON'T**:
- Remove fields without deprecation period
- Change field types without migration
- Bump version for non-breaking changes
- Skip testing old JSON compatibility

---

## References

- `core/src/main/kotlin/core/serde/CanonicalJson.kt` — Serialization implementation
- `core/src/main/kotlin/core/serde/StateDto.kt` — DTO definitions
- `core-test/src/test/kotlin/test/P1_011_SerializationTest.kt` — Stability tests
- `P1_015_GoldenReplaysTest.kt` — Golden state generation
