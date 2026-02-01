# KDoc Documentation Plan

## Target Directory
`core/src/main/kotlin/core/` (all subdirectories)

## Recent changes (action required)

NOTE: Treat this plan as updated after FP-ECON-02 (Money Contract) refactor —
the repository recently added a Money primitive and changed pricing/generation
APIs that affect canonical units and rounding semantics.

High-level summary of recent changes you must document:
- New money primitive and API: `core/primitives/Money.kt` (MoneyCopper, Money
  object, conversions, mulFractionBp). This defines canonical storage unit
  (copper) and conversion helpers (1 gp = 100 copper) and rounding rules
  (floor on decimal → copper).
- Pricing API updated: `core/flavour/ContractPricing.kt` now exposes
  `samplePayoutMoney()` and `sampleClientDepositMoney()` returning
  `MoneyCopper`. Old GP-returning methods were deprecated.
- Inbox generator changed: `core/pipeline/InboxLifecycle.kt` now populates
  `ContractDraft.feeOffered` and `clientDeposit` from the Money-based pricing
  (copper) instead of hardcoded zeros or GP-int values.
- Tests added: `core-test/src/test/kotlin/test/MoneyTest.kt` and
  `core-test/src/test/kotlin/test/MoneyIntegrationTest.kt` to cover Money
  conversions and integration behavior.

Why this matters to docs
- The canonical monetary unit used across `core` is now copper (Int) for
  storage. Computations use deterministic BigDecimal math and floor rounding to
  copper.
- Several public APIs changed semantics (return types/units). Documentation
  must explicitly state units to avoid confusion between design-level GP
  bands (BalanceSettings) and storage-level copper values.

Implication for `DocsPlan.md`:
- Mark this plan as updated and insert the Recent changes section above.
- Add `Money.kt` to the `primitives/` inventory and document it as a
  top-priority KDoc target.
- Add `ContractPricing.kt` and `InboxLifecycle.kt` to the 'Files Requiring
  KDoc Addition / Review' list, with explicit tasks (document new methods,
  deprecations, and conversion/rounding rules).
- Note tests and golden baselines that may need rebaselining, and list the
  likely places to update examples (adapter-console golden outputs).

---

## Kotlin Files (approx. 64 files total)

> NOTE: the file-count here was updated to reflect the newly added `Money.kt` in
> `primitives/`.

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
- `primitives/` (12 files)  <!-- updated: added Money.kt -->
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

### primitives/ (12 files)

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
| Money.kt             | `MoneyCopper` / `Money` | HAS_KDOC (NEW) |

> Note: `Money.kt` was recently added as part of FP-ECON-02. It must be documented as high-priority if not already.

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
| InboxLifecycle.kt      | NEEDS_KDOC_UPDATE (was changed by FP-ECON-02) |
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
| flavour/    | ContractPricing.kt, HeroNames.kt                            | NEEDS_KDOC_REVIEW
  (ContractPricing changed — document new APIs & deprecations) |

---

## Files Requiring KDoc Addition or Review (PRIORITY LIST)

1. **core/primitives/Money.kt** — HIGH PRIORITY
   - Document `MoneyCopper` value class and `Money` API, conversion functions,
     invariants (floor rounding), and examples (gp→copper conversion,
     mulFractionBp usage).

2. **core/flavour/ContractPricing.kt** — HIGH PRIORITY
   - Document new `samplePayoutMoney()` and `sampleClientDepositMoney()` APIs,
     explain determinism and units (returns `MoneyCopper`), and mark
     deprecated `samplePayoutGp()`/`sampleClientDepositGp()` with migration tips.

3. **core/pipeline/InboxLifecycle.kt** — HIGH PRIORITY (UPDATED)
   - Clarify generator behavior: `feeOffered` and `clientDeposit` are in copper
     and derived from pricing. Show example and explain RNG draw contract.

4. **core/state/ContractStateManagement.kt** — VERIFY KDoc
   - Ensure `ContractDraft.feeOffered`, `BoardContract.fee`, and `clientDeposit`
     properties explicitly state they are copper units and explain
     relationships (clientDeposit <= fee, escrow semantics).

5. **adapter-console/ContractFlavor.kt** — REVIEW
   - Add a short note to presentation docs that displayed fees are copper;
     consider linking to a future money formatter.

6. **core-test/** — Update test documentation
   - Add notes about new Money unit tests (`MoneyTest`) and integration tests
     (`MoneyIntegrationTest`). Indicate which golden outputs may need rebaseline
     (adapter-console golden files).

7. **BalanceSettings.kt** — CLARIFY
   - Add plain-language note: PAYOUT_* ranges are design bands expressed in
     GP; conversion to copper occurs at pricing/generator boundary via Money API.
     Link to `Money.kt`.

8. **serde/StateDto.kt** — VERIFY
   - Ensure DTO docs reflect that fees and deposits are copper ints in serialized
     state.

9. **DocsPlan.md** — THIS FILE: Updated to reflect FP-ECON-02 and to note the
   above action items.

---

## Suggested Doc Text Snippets (for copy/paste into KDoc)

(These are suggestions to accelerate writing full KDoc in the files above.)

- For `MoneyCopper` KDoc (short):

```
/**
 * Money value stored in copper units (1 gp = 100 copper).
 *
 * Invariants:
 * - copper >= 0
 * - All conversions from decimal amounts use floor rounding to copper.
 *
 * Use `Money` helper functions for conversions and fraction calculations.
 */
```

- For `ContractPricing.samplePayoutMoney` KDoc (short):

```
/**
 * Sample a payout for a given rank and return the result in copper units.
 *
 * Note: BalanceSettings.PAYOUT_* are expressed in GP (design bands). This
 * function samples in GP then converts to copper (1 gp = 100 copper) and
 * applies floor rounding. Use this API for downstream storage and display.
 */
```

- For `InboxLifecycle.generateDrafts` update (short):

```
/**
 * Generates ContractDraft entries for the inbox.
 *
 * Each draft's `feeOffered` and `clientDeposit` are expressed in copper and are
 * populated from the Money-aware pricing API. Client deposit calculation uses
 * basis points and is computed in copper (flooring any fractional copper).
 */
```

---

## Test & Verification Notes

- After docs updates, run Dokka to verify generated docs:

```powershell
./gradlew :core:dokkaGeneratePublicationHtml
# open core/build/dokka/html/index.html
```

- Re-run unit and integration tests to ensure textual examples in docs remain accurate.

---

## Next steps (recommended order)

1. Add KDoc to `core/primitives/Money.kt` (if not fully documented) — author: economy lead
2. Update `core/flavour/ContractPricing.kt` KDoc with new API and deprecations — author: pricing owner
3. Update `core/pipeline/InboxLifecycle.kt` KDoc to clarify units and RNG contract — author: pipeline owner
4. Review `core/state/ContractStateManagement.kt` and `serde/StateDto.kt` to ensure serialization docs
   match copper semantics — author: state owner

5. Add an entry in README/adapter-console docs to explain fee display units
   and sample output changes — author: console adapter owner

---

This `DocsPlan.md` is updated to include the FP-ECON-02 changes. Follow the
next steps above to apply KDoc updates in the codebase.

## Verification

Run Dokka and review the generated HTML to ensure the newly documented symbols appear correctly.

```powershell
./gradlew :core:dokkaGeneratePublicationHtml
```

Expected output: `core/build/dokka/html/`
