# Reducer Refactor Analysis

This document analyzes the failing tests after the refactoring of the codebase into decision-centric modules.

## FINAL STATUS: ✅ ALL TESTS PASSING

After forensic analysis and targeted fixes, **all 175 tests in `:core-test:test` now pass**.

## Summary of Fixes Applied

### Fix 1: Auto-close contract semantics (phaseWipAndResolve)
**Problem:** Auto-close (`requiresPlayerClose=false`) was setting active to `RETURN_READY` but emitting `ReturnClosed`, which was logically contradictory and broke invariants.

**Solution:**
- Auto-close sets active to `CLOSED` (not `RETURN_READY`)
- `ReturnPacket` is created for **all** resolves (both manual and auto-close) to maintain the journal
- `requiresPlayerClose` flag distinguishes semantics

### Fix 2: WIP-only input to WipProgression.advance()
**Problem:** `WipProgression.advance()` was receiving CLOSED/RETURN_READY actives and could re-resolve them on subsequent days.

**Solution:**
- Partition active list: WIP vs non-WIP
- Advance only WIP actives
- Merge back + sort by id
- `completedActiveIds` now only contains WIP contracts

### Fix 3: Hero lifecycle for auto-close
**Problem:** `HeroLifecycle.computePostResolution()` kept heroes `ON_MISSION` even for auto-close, causing `HEROES__ON_MISSION_IN_EXACTLY_ONE_ACTIVE_CONTRACT` violation when active was `CLOSED`.

**Solution:**
- Added `requiresPlayerClose` parameter to `computePostResolution()`
- For `requiresPlayerClose=false`: hero becomes `AVAILABLE` immediately
- For `requiresPlayerClose=true`: hero stays `ON_MISSION` until `CloseReturn`

### Fix 4: DEATH outcome handling
**Problem:** DEATH was calling `TheftModel` and could produce trophies/theft, and wasn't counted as a failure for stability.

**Solution:**
- DEATH skips theft entirely (trophies=0, suspectedTheft=false)
- DEATH counts as failure for stability calculation
- `arrivalsToday` is filtered to stay subset of roster after hero removal

### Fix 5: Escrow accounting model
**Problem:** `computePostContractDelta()` was subtracting `clientDeposit` from money, but tests expected money to **increase** (guild receives deposit from client).

**Solution:**
- Post: `moneyDelta = +clientDeposit`, `reservedDelta = +clientDeposit`
- Cancel: `moneyDelta = -clientDeposit`, `reservedDelta = -clientDeposit`

## Files Modified

1. `core/src/main/kotlin/core/Reducer.kt`
   - WIP-only filtering for `WipProgression.advance()`
   - ReturnPacket creation before branch (all resolves)
   - DEATH special-casing (no theft, count as fail)
   - Pass `requiresPlayerClose=false` to HeroLifecycle for auto-close
   - arrivalsToday safety clamp

2. `core/src/main/kotlin/core/pipeline/HeroLifecycle.kt`
   - Added `requiresPlayerClose` parameter to `computePostResolution()`
   - Auto-close sets hero to `AVAILABLE`, manual-close keeps `ON_MISSION`

3. `core/src/main/kotlin/core/pipeline/EconomySettlement.kt`
   - Fixed `computePostContractDelta()`: money increases by clientDeposit
   - Fixed `computeCancelContractDelta()`: symmetric refund

## Verification

- All 175 tests pass
- Clean build passes
- Determinism verified (two consecutive runs produce same results)
