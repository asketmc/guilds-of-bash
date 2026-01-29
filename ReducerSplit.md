# Reducer.kt Refactoring Analysis

## Current Status
- **Current Size**: 1383 lines
- **Goal**: Transform Reducer.kt into a thin orchestrator

---

## Architecture Analysis: What Should Stay vs. Move

### ✅ MUST STAY in Reducer.kt (Orchestrator Core)
These are the **non-negotiable** responsibilities that define the reducer pattern:

| Component                | Lines   | Reason                                                                   |
|--------------------------|---------|--------------------------------------------------------------------------|
| `step()` function        | 172-231 | **Single entry point** - command routing, validation, invariant checking |
| `SeqContext` class       | 67-118  | **Event sequencing** - must be co-located with emission                  |
| `assignSeq()` helper     | 124-153 | **Event infrastructure** - tightly coupled to SeqContext                 |
| `StepResult` data class  | 44-49   | **Public API** - contract with callers                                   |
| Command routing (`when`) | 202-212 | **Orchestration** - the core dispatch logic                              |

**Estimated orchestrator core: ~150 lines**

---

### 🔄 SAFE TO EXTRACT (Handler Groups)

#### 1. **AdvanceDay Handlers** → `handlers/AdvanceDayHandler.kt`
| Function                   | Lines   | Description                  |
|----------------------------|---------|------------------------------|
| `handleAdvanceDay`         | 248-280 | Main day orchestration       |
| `phaseInboxGeneration`     | 290-321 | Inbox draft generation       |
| `phaseHeroArrivals`        | 330-361 | Hero arrival handling        |
| `phaseAutoResolveInbox`    | 373-426 | Auto-resolve stale drafts    |
| `phasePickup`              | 437-493 | Hero contract pickup         |
| `phaseWipAndResolve`       | 505-741 | WIP advancement + resolution |
| `phaseStabilityUpdate`     | 752-779 | Stability calculation        |
| `phaseTax`                 | 819-883 | Tax evaluation               |
| `phaseDayEndedAndSnapshot` | 789-807 | Day-end snapshot             |
| `WipResolveResult`         | 505-509 | Internal data class          |

**Total: ~550 lines → Reduces Reducer.kt by ~40%**

#### 2. **Contract Command Handlers** → `handlers/ContractHandlers.kt`
| Function                    | Lines     | Description            |
|-----------------------------|-----------|------------------------|
| `handlePostContract`        | 942-988   | Post draft to board    |
| `handleCreateContract`      | 1192-1233 | Create new draft       |
| `handleUpdateContractTerms` | 1246-1319 | Modify contract terms  |
| `handleCancelContract`      | 1332-1382 | Cancel contract        |
| `handleCloseReturn`         | 1001-1093 | Close completed return |

**Total: ~230 lines**

#### 3. **Economy Command Handlers** → `handlers/EconomyHandlers.kt`
| Function             | Lines     | Description              |
|----------------------|-----------|--------------------------|
| `handleSellTrophies` | 899-929   | Sell trophies for copper |
| `handlePayTax`       | 1106-1145 | Pay tax debt             |

**Total: ~70 lines**

#### 4. **Governance Command Handlers** → `handlers/GovernanceHandlers.kt`
| Function               | Lines     | Description         |
|------------------------|-----------|---------------------|
| `handleSetProofPolicy` | 1157-1181 | Change proof policy |

**Total: ~25 lines**

---

## Risk Assessment

### ⚠️ HIGH RISK - DO NOT EXTRACT
| Item                   | Risk                             | Mitigation         |
|------------------------|----------------------------------|--------------------|
| `SeqContext`           | Event ordering breaks if misused | Keep in Reducer.kt |
| `assignSeq()`          | Coupled to all event types       | Keep in Reducer.kt |
| Invariant verification | Must run after ALL handlers      | Keep in `step()`   |

### ⚡ MEDIUM RISK - Extract with Care
| Item              | Risk                             | Mitigation |
|-------------------|----------------------------------|------------|
| AdvanceDay phases | RNG draw order is contract       | Document draw order |
| State mutations   | Copy semantics must be preserved | Use `internal` visibility |
| Event emission    | Must go through SeqContext       | Pass `ctx` parameter |

### ✅ LOW RISK - Safe to Extract
| Item | Risk | Mitigation |
|------|------|------------|
| Contract handlers | Self-contained | Standard handler pattern |
| Economy handlers | Simple transformations | Already delegates to pipeline |
| Governance handlers | Trivial logic | Already delegates to pipeline |

---

## Recommended Extraction Strategy

### Phase 1: Extract Command Handlers (Low Risk)
```
core/
├── Reducer.kt              (~400 lines - orchestrator)
└── handlers/
    ├── ContractHandlers.kt (~230 lines)
    ├── EconomyHandlers.kt  (~70 lines)
    └── GovernanceHandlers.kt (~25 lines)
```

### Phase 2: Extract AdvanceDay (Medium Risk)
```
core/
├── Reducer.kt              (~150 lines - pure orchestrator)
└── handlers/
    ├── AdvanceDayHandler.kt (~550 lines)
    ├── ContractHandlers.kt
    ├── EconomyHandlers.kt
    └── GovernanceHandlers.kt
```

---

## Enterprise QA Considerations

### ✅ Benefits
1. **Single Responsibility**: Each handler file owns one domain
2. **Testability**: Handlers can be unit-tested in isolation
3. **Code Navigation**: Easier to find relevant code
4. **Team Scalability**: Multiple devs can work on different handlers
5. **Future-Proofing**: New commands go in appropriate handler file

### ⚠️ Risks to Mitigate
1. **Visibility**: Handlers must be `internal` to prevent external misuse
2. **RNG Contract**: Document draw order in AdvanceDayHandler
3. **Event Emission**: All handlers receive `SeqContext`, never create their own
4. **State Immutability**: Handlers return new state, never mutate

### 🔒 Invariants to Preserve
1. **Determinism**: Same inputs → same outputs (RNG draw order critical)
2. **Event Ordering**: SeqContext is the single source of truth
3. **Revision Increment**: Only happens once in `step()`
4. **Invariant Checking**: Only happens after handler completes

---

## Current Structure (After Phase 1)

| File | Lines | Purpose |
|------|-------|---------|
| `Reducer.kt` | **898** | Orchestrator + AdvanceDay phases |
| `handlers/ContractHandlers.kt` | ~320 | Contract CRUD + CloseReturn |
| `handlers/EconomyHandlers.kt` | ~105 | Trophy/money operations |
| `handlers/GovernanceHandlers.kt` | ~55 | Policy changes |

## Next Step: Phase 2 (Optional)

Extract `AdvanceDayHandler.kt` (~550 lines) to further reduce Reducer.kt to ~350 lines.

**Risk**: Medium - RNG draw order is contract-critical for replay determinism.

---

## Decision: Proceed with Phase 2?

Phase 1 is complete. Reducer.kt reduced from 1429 → 898 lines (37% reduction).

Phase 2 would extract AdvanceDay phases but requires careful RNG documentation.

## Pipeline Models Analysis

| Model               | File                   | Lines  | Semantic Ownership               | Already Used  |
|---------------------|------------------------|--------|----------------------------------|---------------|
| ContractPickupModel | ContractPickupModel.kt | 183    | Hero pickup decisions            | Yes           |
| EconomySettlement   | EconomySettlement.kt   | 205    | Money/escrow/trophy calculations | Yes           |
| GovernancePolicy    | GovernancePolicy.kt    | 45     | Proof policy rules               | Yes           |
| GuildProgression    | GuildProgression.kt    | 81     | Rank progression                 | Yes           |
| HeroLifecycle       | HeroLifecycle.kt       | 131    | Hero status changes              | Yes           |
| HeroSupplyModel     | HeroSupplyModel.kt     | ~100   | Hero generation                  | Yes           |
| InboxLifecycle      | InboxLifecycle.kt      | 103    | Draft generation                 | Yes           |
| OutcomeResolution   | OutcomeResolution.kt   | 121    | Success/Fail/Death outcomes      | Yes           |
| ReturnClosurePolicy | ReturnClosurePolicy.kt | 66     | Manual close rules               | Yes           |
| StabilityModel      | StabilityModel.kt      | 82     | Stability calculations           | Yes           |
| TaxPolicy           | TaxPolicy.kt           | 159    | Tax due/payment logic            | Yes           |
| TheftModel          | TheftModel.kt          | 106    | Theft detection                  | Yes           |
| WipProgression      | WipProgression.kt      | 91     | WIP countdown                    | Yes           |
| AutoResolveModel    | AutoResolveModel.kt    | 108    | Inbox auto-resolve               | Yes (NEW)     |
| ResolutionModel     | ResolutionModel.kt     | 111    | Contract resolution              | Yes (NEW)     |
| BoardStatusModel    | BoardStatusModel.kt    | 85     | Board completion                 | Yes (NEW)     |
| SnapshotModel       | SnapshotModel.kt       | 48     | Day snapshot construction        | Yes (NEW)     |

## Opportunities for Extraction

### 1. Auto-Resolve Inbox Phase (~80 lines → new AutoResolveModel.kt)
**Lines**: 346-444
**Current Location**: `phaseAutoResolveInbox` in Reducer.kt
**Refactoring**: Extract bucket decision, inbox update, and stability penalty logic to a new pipeline model.

### 2. Event Assignment (~30 lines → inline via sealed class method)
**Lines**: 111-139
**Current Location**: `assignSeq` function
**Refactoring**: Could use reflection or sealed class visitor pattern, but risky for determinism. LOW PRIORITY.

### 3. WIP Resolve Auto-Close Settlement (~90 lines)
**Lines**: 662-760
**Current Location**: Inside `phaseWipAndResolve` auto-close branch
**Refactoring**: The settlement assembly could be partially delegated to a new model, but event emission must stay in Reducer.

### 4. Day Ended Snapshot (~30 lines)
**Lines**: 812-844
**Current Location**: `phaseDayEndedAndSnapshot`
**Refactoring**: Snapshot construction could move to a model, but minimal gain.

---

## Refactoring Steps

### Step 1: Extract AutoResolveInboxModel (Est. -60 to -80 lines)
Create `pipeline/AutoResolveModel.kt` to handle:
- Bucket decision (GOOD/NEUTRAL/BAD)
- Inbox updates (remove, reschedule, or remove with penalty)
- Stability penalty accumulation

Status: **PENDING**

---

## Progress Log

| Step  | Action                        | Lines Removed | New Lines Added | Reducer Lines | Tests Pass   |
|-------|-------------------------------|---------------|-----------------|---------------|--------------|
| 0     | Initial                       | 0             | 0               | 1429          | -            |
| 1     | Extract AutoResolveModel      | -21           | +108 (new file) | 1408          | ✅            |
| 2     | Extract ResolutionModel       | -11           | +111 (new file) | 1397          | ✅            |
| 3     | Extract BoardStatusModel      | -5            | +85 (new file)  | 1392          | ✅            |
| 4     | Extract SnapshotModel         | -11           | +48 (new file)  | 1381          | ✅            |
| 5     | Move hard-coded → BalanceSettings | +2 (format) | +12 (constants) | 1383          | ✅            |
| 6     | **Phase 1: Extract Handlers** | -485          | +430 (3 files)  | **898**       | ✅            |

**Total Reduction**: 1429 → 898 = **531 lines removed** from Reducer.kt (37% reduction)

### Phase 1 Extraction Details (Step 6)

**Created Files:**
- `handlers/ContractHandlers.kt` (~320 lines)
  - `handlePostContract`, `handleCreateContract`, `handleUpdateContractTerms`
  - `handleCancelContract`, `handleCloseReturn`
- `handlers/EconomyHandlers.kt` (~105 lines)
  - `handleSellTrophies`, `handlePayTax`
- `handlers/GovernanceHandlers.kt` (~55 lines)
  - `handleSetProofPolicy`

**Reducer.kt now contains:**
- `step()` function - main entry point
- `SeqContext` class - event sequencing
- `assignSeq()` helper - event infrastructure
- `handleAdvanceDay` + all phase functions (~650 lines)
- Command routing (`when` dispatch)

---

## Step Details

### Step 1: AutoResolveModel Extraction

**Created**: `core/src/main/kotlin/core/pipeline/AutoResolveModel.kt` (108 lines)

**Changes in Reducer.kt**:
- Replaced inline bucket decision logic in `phaseAutoResolveInbox` with delegation to `AutoResolveModel.computeAutoResolve()`
- Removed ~21 lines of inline logic
- Event emission and stability application remain in Reducer.kt (proper separation)

**Result**: Reducer reduced from 1429 → 1408 lines

---

### Step 2: ResolutionModel Extraction

**Created**: `core/src/main/kotlin/core/pipeline/ResolutionModel.kt` (111 lines)

**Changes in Reducer.kt**:
- Combined `OutcomeResolution.decide()` + `TheftModel.decide()` + DEATH override logic into `ResolutionModel.computeResolution()`
- Extracted stability contribution tracking to `ResolutionModel.computeStabilityContribution()`
- Reducer now uses single `resolution` object instead of separate `outcomeDecision` and `theftDecision`

**Result**: Reducer reduced from 1408 → 1397 lines

---

### Step 3: BoardStatusModel Extraction

**Created**: `core/src/main/kotlin/core/pipeline/BoardStatusModel.kt` (85 lines)

**Changes in Reducer.kt**:
- Replaced inline board status completion check in `handleCloseReturn` with `BoardStatusModel.updateBoardStatus()`
- Simplified board completion logic from 7 lines to 4 lines

**Result**: Reducer reduced from 1397 → 1392 lines

---

### Step 4: SnapshotModel Extraction

**Created**: `core/src/main/kotlin/core/pipeline/SnapshotModel.kt` (48 lines)

**Changes in Reducer.kt**:
- Extracted DaySnapshot construction from `phaseDayEndedAndSnapshot` to `SnapshotModel.createDaySnapshot()`
- Simplified day-end phase from inline construction to single delegation call

**Result**: Reducer reduced from 1392 → 1381 lines

---

### Step 5: Move Hard-coded Values to BalanceSettings

**Added to BalanceSettings.kt**:
- `STABILITY_MIN = 0` - Minimum stability value
- `STABILITY_MAX = 100` - Maximum stability value
- `RANK_MULTIPLIER_BASE = 2` - Base multiplier for inbox/heroes per rank
- `DEFAULT_CONTRACT_DIFFICULTY = 1` - Fallback difficulty when board contract missing

**Changes in Reducer.kt**:
- Line 264-265: `* 2` → `* BalanceSettings.RANK_MULTIPLIER_BASE`
- Line 408: `.coerceIn(0, 100)` → `.coerceIn(STABILITY_MIN, STABILITY_MAX)`
- Line 556: `?: 1` → `?: BalanceSettings.DEFAULT_CONTRACT_DIFFICULTY`

**Also updated**: `StabilityModel.kt` - Both `.coerceIn(0, 100)` calls now use constants

**Result**: Code slightly longer due to formatting, but all balance values now centralized

---

## Further Opportunities (Not Yet Implemented)

### Event Assignment (~30 lines)
**Location**: `assignSeq` function (lines 111-139)
**Status**: LOW PRIORITY - Could use reflection or sealed class visitor pattern, but risky for determinism.

### Day Ended Snapshot (~30 lines)
**Location**: `phaseDayEndedAndSnapshot`
**Status**: Minimal gain - snapshot construction is straightforward.

### Auto-close Settlement Assembly
**Location**: Inside `phaseWipAndResolve` auto-close branch
**Status**: Complex - event emission must stay in Reducer, limited extraction possible.
