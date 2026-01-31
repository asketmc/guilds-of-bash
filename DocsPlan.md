# KDoc Documentation Plan

## Target Directory
`core/src/main/kotlin/core/` (all subdirectories)

## Kotlin Files (63 files total)

### Root files (14 files)
- BalanceSettings.kt
- Commands.kt
- CommandValidation.kt
- Compat.kt
- ContractAttractiveness.kt
- Events.kt
- HeroPower.kt
- RankThreshold.kt
- ReasonTag.kt
- Reducer.kt
- TaxCalculation.kt
- ThreatScaling.kt

### Subdirectories
- `state/` (8 files) - HAS_KDOC ✅
- `primitives/` (11 files)
- `handlers/` (4 files)
- `pipeline/` (17 files)
- `hash/` (1 file)
- `serde/` (3 files)
- `invariants/` (3 files)
- `partial/` (1 file)
- `rng/` (1 file)
- `flavour/` (2 files)

---

## Declaration Inventory

### state/ (8 files) — HAS_KDOC ✅
All 15 public declarations documented.

### Root files

| File                      | Declaration                 | Type             | KDoc Status  |
|---------------------------|-----------------------------|------------------|--------------|
| BalanceSettings.kt        | `BalanceSettings`           | object           | HAS_KDOC     |
| Commands.kt               | `Command`                   | sealed interface | MISSING_KDOC |
| Commands.kt               | `PostContract`              | data class       | MISSING_KDOC |
| Commands.kt               | `CloseReturn`               | data class       | MISSING_KDOC |
| Commands.kt               | `AdvanceDay`                | data class       | MISSING_KDOC |
| Commands.kt               | `SellTrophies`              | data class       | MISSING_KDOC |
| Commands.kt               | `PayTax`                    | data class       | MISSING_KDOC |
| Commands.kt               | `SetProofPolicy`            | data class       | MISSING_KDOC |
| Commands.kt               | `CreateContract`            | data class       | MISSING_KDOC |
| Commands.kt               | `UpdateContractTerms`       | data class       | MISSING_KDOC |
| Commands.kt               | `CancelContract`            | data class       | MISSING_KDOC |
| CommandValidation.kt      | `RejectReason`              | enum             | HAS_KDOC     |
| CommandValidation.kt      | `ValidationResult`          | sealed interface | HAS_KDOC     |
| CommandValidation.kt      | `canApply()`                | function         | HAS_KDOC     |
| Compat.kt                 | (extensions)                | extensions       | MISSING_KDOC |
| ContractAttractiveness.kt | `ContractAttractiveness`    | data class       | HAS_KDOC     |
| ContractAttractiveness.kt | `evaluateContractForHero()` | function         | HAS_KDOC     |
| Events.kt                 | `Event`                     | sealed interface | HAS_KDOC     |
| Events.kt                 | (30+ event classes)         | data classes     | MISSING_KDOC |
| HeroPower.kt              | `calculateHeroPower()`      | function         | MISSING_KDOC |
| RankThreshold.kt          | `RankThreshold`             | data class       | MISSING_KDOC |
| RankThreshold.kt          | `RANK_THRESHOLDS`           | val              | MISSING_KDOC |
| RankThreshold.kt          | `calculateNextRank()`       | function         | HAS_KDOC     |
| ReasonTag.kt              | `ReasonTag`                 | enum             | MISSING_KDOC |
| Reducer.kt                | `StepResult`                | data class       | HAS_KDOC     |
| Reducer.kt                | `SeqContext`                | class            | HAS_KDOC     |
| Reducer.kt                | `step()`                    | function         | HAS_KDOC     |
| TaxCalculation.kt         | `calculateTaxAmount()`      | function         | MISSING_KDOC |
| ThreatScaling.kt          | `calculateThreatLevel()`    | function         | MISSING_KDOC |
| ThreatScaling.kt          | `calculateBaseDifficulty()` | function         | MISSING_KDOC |

### primitives/ (11 files)

| File                 | Declaration         | KDoc Status  |
|----------------------|---------------------|--------------|
| ActiveStatus.kt      | `ActiveStatus`      | HAS_KDOC     |
| AutoResolveBucket.kt | `AutoResolveBucket` | HAS_KDOC     |
| BoardStatus.kt       | `BoardStatus`       | HAS_KDOC     |
| HeroClass.kt         | `HeroClass`         | HAS_KDOC     |
| HeroStatus.kt        | `HeroStatus`        | HAS_KDOC     |
| IDs.kt               | `ContractId`        | HAS_KDOC     |
| IDs.kt               | `HeroId`            | HAS_KDOC     |
| IDs.kt               | `ActiveContractId`  | HAS_KDOC     |
| Outcome.kt           | `Outcome`           | HAS_KDOC     |
| ProofPolicy.kt       | `ProofPolicy`       | HAS_KDOC     |
| Quality.kt           | `Quality`           | HAS_KDOC     |
| Rank.kt              | `Rank`              | HAS_KDOC     |
| SalvagePolicy.kt     | `SalvagePolicy`     | HAS_KDOC     |

### handlers/ (4 files)

| File                  | Declaration          | KDoc Status  |
|-----------------------|----------------------|--------------|
| AdvanceDayHandler.kt  | `handleAdvanceDay()` | HAS_KDOC     |
| AdvanceDayHandler.kt  | `WipResolveResult`   | HAS_KDOC     |
| ContractHandlers.kt   | All handlers         | HAS_KDOC     |
| EconomyHandlers.kt    | All handlers         | HAS_KDOC     |
| GovernanceHandlers.kt | All handlers         | HAS_KDOC     |

### pipeline/ (17 files)

| File                   | KDoc Status  |
|------------------------|--------------|
| AutoResolveModel.kt    | HAS_KDOC     |
| BoardStatusModel.kt    | HAS_KDOC     |
| ContractPickupModel.kt | HAS_KDOC     |
| EconomySettlement.kt   | HAS_KDOC     |
| GovernancePolicy.kt    | HAS_KDOC     |
| GuildProgression.kt    | HAS_KDOC     |
| HeroLifecycle.kt       | HAS_KDOC     |
| HeroSupplyModel.kt     | HAS_KDOC     |
| InboxLifecycle.kt      | HAS_KDOC     |
| OutcomeResolution.kt   | HAS_KDOC     |
| ResolutionModel.kt     | HAS_KDOC     |
| ReturnClosurePolicy.kt | HAS_KDOC     |
| SnapshotModel.kt       | HAS_KDOC     |
| StabilityModel.kt      | HAS_KDOC     |
| TaxPolicy.kt           | HAS_KDOC     |
| TheftModel.kt          | HAS_KDOC     |
| WipProgression.kt      | HAS_KDOC     |

### Other subdirectories

| Directory   | Files                                                       | KDoc Status  |
|-------------|-------------------------------------------------------------|--------------|
| hash/       | Hashing.kt                                                  | HAS_KDOC     |
| serde/      | CanonicalJson.kt, CanonicalEventsJson.kt, StateDto.kt       | PARTIAL      |
| invariants/ | InvariantId.kt, InvariantVerifier.kt, InvariantViolation.kt | HAS_KDOC     |
| partial/    | PartialOutcomeResolver.kt                                   | HAS_KDOC     |
| rng/        | Rng.kt                                                      | HAS_KDOC     |
| flavour/    | ContractPricing.kt, HeroNames.kt                            | HAS_KDOC     |

---

## Files Requiring KDoc Addition

1. **Commands.kt** — All command types ✅ COMPLETED
2. **Events.kt** — Event data classes ✅ COMPLETED
3. **Compat.kt** — Compatibility helpers ✅ COMPLETED
4. **HeroPower.kt** — `calculateHeroPower()` ✅ COMPLETED
5. **RankThreshold.kt** — `RankThreshold` and `RANK_THRESHOLDS` ✅ COMPLETED
6. **ReasonTag.kt** — `ReasonTag` enum ✅ COMPLETED
7. **TaxCalculation.kt** — `calculateTaxAmount()` ✅ COMPLETED
8. **ThreatScaling.kt** — Both functions ✅ COMPLETED
9. **serde/StateDto.kt** — DTO classes ✅ COMPLETED

---

## Status

✅ **All public declarations in `core/src/main/kotlin/core/` now have KDoc documentation.**

Documentation follows the template structure with:
- `## Role` or `## Contract` sections
- `## Invariants` sections (where applicable)
- `## Determinism` sections (where applicable)
- `@property` tags for data class properties
- `@param` and `@return` tags for functions

---

## Verification

Run Dokka to confirm HTML generation:
```
./gradlew :core:dokkaGeneratePublicationHtml
```

Expected output location: `core/build/dokka/html/`
