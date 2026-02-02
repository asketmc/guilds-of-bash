# Test Review: core-test/src/test/kotlin/test/

Generated: 2026-01-31

## Summary

| File                                | Lines  | Tests  | What They Check                                                                          | Comm-ent             |
|-------------------------------------|--------|--------|------------------------------------------------------------------------------------------|----------------------|
| AbuseTechnicalPoCTest.kt            | 309    | 3      | Anti-abuse safeguards: replay protection (absence), state immutability, sell boundary    | P2                   |
| CancelContractTest.kt               | 334    | 4      | CancelContract command: inbox removal, board removal with escrow refund, rejections      | P2                   |
| ClientDepositEscrowInvariantTest.kt | 146    | 4      | Client deposit escrow: hold on post, settlement on close, idempotency, timing            | P1                   |
| CommandValidationTest.kt            | 206    | 11     | canApply validation: AdvanceDay, PostContract, CloseReturn preconditions                 | P1, Smoke            |
| ContractExpiryPoCTest.kt            | 417    | 4+     | Contract auto-resolve: not due, GOOD/NEUTRAL/BAD buckets, stability delta                | P2                   |
| ContractPricingTest.kt              | 245    | 14     | Rank-based pricing: payout bands (F-S), client deposit, tail distribution, determinism   | P2                   |
| CorePerfLoadTest.kt                 | 108    | 1      | Performance/load smoke: baseline AdvanceDay, post scenarios                              | P3/Perf, manual only |
| CreateContractTest.kt               | 319    | 5+     | CreateContract command: draft creation, validation (blank title, difficulty, reward)     | P2                   |
| EdgeCasesPoCTest.kt                 | 244    | 4      | Edge cases: empty board, double-take tie-break, process-twice rejection                  | P2                   |
| FeeEscrowTest.kt                    | 254    | 8      | Fee escrow accounting: post reserves, close pays, take no payout, clientDeposit coverage | P2                   |
| GameStateInitializationTest.kt      | 27     | 1      | Initial state defaults: money, trophies, inbox size                                      | P0, Smoke            |
| GoldenReplaysTest.kt                | 254    | 3      | Golden replay scenarios: GR1 happy path, GR2 rejections, determinism                     | P1, Smoke            |
| HashingTest.kt                      | 217    | 7      | Hashing: hashState/hashEvents format, determinism, change detection                      | P1                   |
| HeroNamesTest.kt                    | 63     | 2      | Hero naming: names from fixed pool, determinism                                          | P1                   |
| InvariantsAfterEachStepTest.kt      | 293    | 4      | Invariants hold after every step in GR1/GR2/GR3 scenarios                                | P1, Smoke            |
| InvariantVerificationTest.kt        | 212    | 8      | Invariant verification: economy, bounds, ID counters, active constraints                 | P1, Smoke            |
| LockedBoardInvariantTest.kt         | 140    | 5      | LOCKED board invariant: unlock on close, multiple actives, status transitions            | P1                   |
| MissingOutcomeTest.kt               | 35     | 1      | MISSING outcome: scripted RNG forces death-like outcome                                  | P1                   |
| OutcomeBranchesTest.kt              | 340    | 4+     | Outcome branches: SUCCESS/PARTIAL/FAIL/DEATH reachability, determinism                   | P2                   |
| PoCManifestCoverageTest.kt          | 175    | 5      | PoC manifest coverage: commands/events exist, no scope creep                             | P1                   |
| PoCScenarioTest.kt                  | 89     | 1      | End-to-end PoC scenario: contract flow from post to resolve                              | P1                   |
| ReducerCriticalTest.kt              | 40     | 2      | Reducer critical: invariants preserved, invalid cmdId rejected                           | P0, Smoke            |
| ReturnClosureDecisionTest.kt        | 502    | 19     | Return closure accept/reject: STRICT unblocking, escrow, counters, events, determinism   | P1, Smoke (ADR-004)  |
| RngContractTest.kt                  | 49     | 2      | RNG contract: draws counter, bounded methods                                             | P1                   |
| RngDrawOrderGoldenTest.kt           | 205    | 5      | RNG draw order: stability, determinism per-seed, documented draws                        | P1                   |
| RngSeedFinder.kt                    | 42     | 1      | Utility: find seeds for GOOD/NEUTRAL/BAD buckets                                         | Utility              |
| SellTrophiesTest.kt                 | 169    | 10     | SellTrophies command: sell all, partial, validation, revision increment                  | P2                   |
| SerializationTest.kt                | 303    | 6      | Event serialization: branch coverage, field order, escaping, nullables, arrays           | P1                   |
| StabilityUpdatedTest.kt             | 168    | 4      | StabilityUpdated event: no event when no returns, delta calculation, clamping            | P2                   |
| TrophyPipelineTest.kt               | 115    | 5      | Trophy pipeline: resolve creates return, close deposits, sell increases money            | P1                   |
| UpdateContractTermsTest.kt          | 341    | 8+     | UpdateContractTerms: inbox/board updates, escrow adjustment, validation                  | P2                   |

---

## Potential Duplicates / Overlap Analysis

### ✅ No Exact Duplicates Found

All test files serve distinct purposes. However, there is some **functional overlap** in areas:

### 1. Escrow/Economy Tests (Related but Distinct)

| File                                | Focus                                         |
|-------------------------------------|-----------------------------------------------|
| FeeEscrowTest.kt                    | Fee escrow accounting model (post/close/take) |
| ClientDepositEscrowInvariantTest.kt | Client deposit escrow invariants              |
| CancelContractTest.kt               | Escrow refund on cancel                       |

**Verdict**: Keep all. They test different aspects of the same system.

### 2. Golden Replay / Scenario Tests (Complementary)

| File                           | Focus                                         |
|--------------------------------|-----------------------------------------------|
| GoldenReplaysTest.kt           | Full lifecycle replays with hash verification |
| PoCScenarioTest.kt             | End-to-end contract flow                      |
| InvariantsAfterEachStepTest.kt | Invariants at each step of scenarios          |

**Verdict**: Keep all. GoldenReplays tests determinism, PoCScenario tests flow, InvariantsAfterEachStep tests per-step invariants.

### 3. Invariant Tests (Complementary)

| File                           | Focus                                     |
|--------------------------------|-------------------------------------------|
| InvariantVerificationTest.kt   | Unit tests for individual invariant rules |
| InvariantsAfterEachStepTest.kt | Invariants hold through scenarios         |
| LockedBoardInvariantTest.kt    | Specific LOCKED board invariant           |

**Verdict**: Keep all. Different granularity levels.

### 4. Command Validation Tests (Some Overlap)

| File                       | Focus                                     |
|----------------------------|-------------------------------------------|
| CommandValidationTest.kt   | General canApply validation               |
| CreateContractTest.kt      | CreateContract validation + behavior      |
| CancelContractTest.kt      | CancelContract validation + behavior      |
| UpdateContractTermsTest.kt | UpdateContractTerms validation + behavior |

**Verdict**: Keep all. CommandValidationTest covers basic validation; others are command-specific.

### 5. RNG Tests (Distinct)

| File                      | Focus                                      |
|---------------------------|--------------------------------------------|
| RngContractTest.kt        | Rng class contract (draws counter, bounds) |
| RngDrawOrderGoldenTest.kt | Draw count stability across scenarios      |
| MissingOutcomeTest.kt     | ScriptedRng for specific outcome           |
| RngSeedFinder.kt          | Utility to find specific seeds             |

**Verdict**: Keep all. Different purposes.

---

## Recommendations

1. **No files to remove** - All test files serve distinct purposes.

2. **Consider consolidating** if tests grow:
   - Escrow tests could be grouped in a package
   - Golden replay tests could be grouped in a package

3. **RngSeedFinder.kt** - This is a utility, not a real test. Consider moving to a `tools/` package or marking with `@Disabled`.

4. **CorePerfLoadTest.kt** - Already properly excluded from CI via `@Perf` tag.

---

## Test Priority Distribution

| Priority  | Count  | Description                                                  |
|-----------|--------|--------------------------------------------------------------|
| P0        | 2      | Critical startup (GameStateInitialization, ReducerCritical)  |
| P1        | 16     | Core functionality (hashing, invariants, serialization, RNG, return closure decisions) |
| P2        | 11     | Feature-level tests (commands, escrow, pricing)              |
| P3/Perf   | 1      | Performance (manual only)                                    |
| Utility   | 1      | RngSeedFinder                                                |

---

## Total Test Count

**~140+ unit tests** across 31 test files.
