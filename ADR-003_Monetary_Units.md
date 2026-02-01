# ADR-003: Monetary Units and Contract Fee Calculation

**Status**: Accepted
**Date**: 2026-01-31
**Scope**: Economy, Contracts, Inbox Lifecycle, Tests
**Owner**: Core
**Type**: Architecture / Bugfix

---

## Context

Balance settings define contract payouts in GP (`PAYOUT_F = 0–1 GP`), while contracts store and display fees in copper.
All F-tier contracts displayed `feeOffered = 0` regardless of RNG seed or game progression.

---

## Problem

### Symptoms

* `feeOffered` always equals `0` for F-tier contracts
* `clientDeposit` always equals `0`
* Integration tests compile but validate incorrect behavior

### Causes

1. Payouts defined in GP, stored in copper without conversion
2. `feeOffered` hardcoded to `0` in `InboxLifecycle`
3. Integer truncation in deposit calculation
4. Integration tests using obsolete APIs

---

## Decision

### 1. Money Primitive

Introduce `Money` value object:

* Internal unit: copper
* Explicit GP → copper conversion
* Floor rounding
* Fraction operations via basis points

---

### 2. Pricing in Copper

Replace GP-based pricing with copper-based API:

```kotlin
samplePayoutMoney(rank, rng): Money
sampleClientDepositMoney(payout, rng): Money
```

Deprecate:

```kotlin
samplePayoutGp(...)
```

---

### 3. Inbox Lifecycle Update

Replace hardcoded values:

```kotlin
feeOffered = 0
clientDeposit = 0
```

With:

```kotlin
val payout = samplePayoutMoney(rank, rng)
feeOffered = payout.copper
clientDeposit = sampleClientDepositMoney(payout, rng).copper
```

---

### 4. Determinism

* RNG draw order unchanged
* Same seed produces identical results

---

### 5. Tests

* Fix integration test imports and API usage
* Add unit and integration tests for:

    * GP → copper conversion
    * Deposit calculation
    * Non-zero F-tier fees
    * Deterministic behavior

---

## Files

### Added

* `core/primitives/Money.kt`
* `core-test/test/MoneyTest.kt`
* `core-test/test/MoneyIntegrationTest.kt`

### Modified

* `core/flavour/ContractPricing.kt`
* `core/pipeline/InboxLifecycle.kt`

---

## Consequences

* Monetary units are explicit and consistent
* Integer truncation removed from fee and deposit logic
* Tests validate monetary behavior
* Legacy tests expecting `feeOffered = 0` require update

---

## Non-Goals

* Balance changes
* UI changes
* Payment probability changes
* Floating-point arithmetic in runtime logic

---

## Validation

```powershell
.\gradlew.bat clean build
.\gradlew.bat :core-test:test --tests "test.Money*"
.\gradlew.bat :adapter-console:run --args="new"
```

---

## Status

Accepted and implemented.

---
