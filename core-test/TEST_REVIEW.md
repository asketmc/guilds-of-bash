# Test Review: core-test/src/test/kotlin/

Generated: 2026-02-02

## Summary

| File                                | What They Check |
|-------------------------------------|-----------------|
| AbuseTechnicalPoCTest.kt            | Anti-abuse and technical safety scenarios |
| ArchiveAutoCloseInvariantTest.kt    | Invariants around contract archive auto-close behavior |
| ArchiveContractTest.kt              | Closing last return archives board contract and preserves invariants |
| CancelContractTest.kt               | CancelContract behavior and validation |
| ClientDepositEscrowInvariantTest.kt | Client deposit escrow lifecycle invariants |
| CommandValidationTest.kt            | `canApply` validation rules for key commands |
| ContractExpiryPoCTest.kt            | Contract expiry and auto-resolve behaviors |
| ContractPricingTest.kt              | Rank-based pricing, payout bands, determinism |
| CorePerfLoadTest.kt                 | Performance load checks via `@Perf` |
| CreateContractTest.kt               | CreateContract behavior and validation |
| EdgeCasesPoCTest.kt                 | Edge conditions across core flows |
| FeeEscrowTest.kt                    | Fee escrow accounting model across post, close, take |
| GameStateInitializationTest.kt      | Initial state defaults |
| GoldenReplaysTest.kt                | Scenario replays and determinism |
| HashingTest.kt                      | State and event hashing determinism |
| HeroNamesTest.kt                    | Deterministic hero naming |
| InvariantsAfterEachStepTest.kt      | Invariants hold after each step in scenarios |
| InvariantVerificationTest.kt        | Targeted invariant rule verification |
| LockedBoardInvariantTest.kt         | Locked board invariant correctness |
| MissingOutcomeTest.kt               | Scripted RNG forces specific outcome branch |
| MoneyIntegrationTest.kt             | Integration coverage for Money contract refactor |
| MoneyTest.kt                        | Money conversion and calculation contract |
| OutcomeBranchesTest.kt              | Outcome branch reachability and determinism |
| PoCManifestCoverageTest.kt          | PoC manifest coverage against commands and events |
| PoCScenarioTest.kt                  | End-to-end PoC scenario |
| ReducerCriticalTest.kt              | Reducer core critical behavior |
| ReturnClosureDecisionTest.kt        | Manual return closure decisions under `ProofPolicy.STRICT` |
| RngContractTest.kt                  | RNG contract validity |
| RngDrawOrderGoldenTest.kt           | RNG draw order stability |
| SellTrophiesTest.kt                 | SellTrophies behavior and validation |
| SerializationTest.kt                | Event serialization format stability |
| StabilityUpdatedTest.kt             | StabilityUpdated event emission and delta calculation |
| TrophyPipelineTest.kt               | Trophy pipeline lifecycle and accounting |
| UpdateContractTermsTest.kt          | UpdateContractTerms behavior and validation |

---

## Notes

- Tag definitions: `core-test/src/test/kotlin/test/Tags.kt`
- Smoke suite selection: `core-test/src/test/kotlin/test/suites/SmokeSuite.kt`
- Test task filters and PR gate composition: `build-logic/src/main/kotlin/gob.core-test.gradle.kts`

---

## Duplicates and overlap

No exact duplicate test files were identified.
Some overlap is intentional between command-specific tests, invariant verification, and scenario replays.

---

## Totals

Totals are intentionally not hard-coded in this review.
Use the current sources and Gradle task selection rules to determine executed test counts.
