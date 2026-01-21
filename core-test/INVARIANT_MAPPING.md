# Invariant Mapping: Doc IDs → Code → Test Coverage

**Purpose**: Maps documentation invariant identifiers to code `InvariantId` enum entries and P1 test coverage.

**Version**: 1.0
**Last Updated**: 2026-01-21

---

## Invariant Categories

### IDs (Identity Monotonicity)

| Doc ID | Code `InvariantId` | Description | P1 Test Coverage | Priority |
|--------|-------------------|-------------|------------------|----------|
| `INV_IDS_001` | `IDS__NEXT_CONTRACT_ID_POSITIVE` | `nextContractId > 0` | `P1_012_GameStateInitializationTest` | P1 Critical |
| `INV_IDS_002` | `IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE` | `nextActiveContractId > 0` | `P1_012_GameStateInitializationTest` | P1 Critical |
| `INV_IDS_003` | `IDS__NEXT_HERO_ID_POSITIVE` | `nextHeroId > 0` | `P1_012_GameStateInitializationTest` | P1 Critical |
| `INV_IDS_004` | `IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID` | `nextContractId > max(inbox+board).id` | `P1_004_InvariantVerificationTest` | P1 Critical |
| `INV_IDS_005` | `IDS__NEXT_ACTIVE_CONTRACT_ID_GT_MAX_ACTIVE_ID` | `nextActiveContractId > max(active).id` | `P1_004_InvariantVerificationTest` | P1 Critical |
| `INV_IDS_006` | `IDS__NEXT_HERO_ID_GT_MAX_HERO_ID` | `nextHeroId > max(roster).id` | `P1_004_InvariantVerificationTest` | P1 Critical |

### Contracts (State Consistency)

| Doc ID | Code `InvariantId` | Description | P1 Test Coverage | Priority |
|--------|-------------------|-------------|------------------|----------|
| `INV_CONTRACTS_001` | `CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE` | Board locked ⇒ ∃ active contract referencing it | `P1_001_InvariantLockedBoardTest` | P1 Critical |
| `INV_CONTRACTS_002` | `CONTRACTS__RETURN_READY_HAS_RETURN_PACKET` | Status=RETURN_READY ⇒ return packet exists | `P1_004_InvariantVerificationTest` | P1 Critical |
| `INV_CONTRACTS_003` | `CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE` | Return packet activeId references existing active contract | `P1_004_InvariantVerificationTest` | P1 Critical |
| `INV_CONTRACTS_004` | `CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE` | `daysRemaining ≥ 0` for all active contracts | `P1_004_InvariantVerificationTest` | P1 Critical |
| `INV_CONTRACTS_005` | `CONTRACTS__WIP_DAYS_REMAINING_IN_1_2` | Status=WIP ⇒ `daysRemaining ∈ {1, 2}` | `P1_004_InvariantVerificationTest` | P1 Critical |

### Heroes (Assignment Consistency)

| Doc ID | Code `InvariantId` | Description | P1 Test Coverage | Priority |
|--------|-------------------|-------------|------------------|----------|
| `INV_HEROES_001` | `HEROES__ON_MISSION_IN_EXACTLY_ONE_ACTIVE_CONTRACT` | Hero with status=ON_MISSION appears in exactly 1 active contract | `P1_004_InvariantVerificationTest` | P1 Critical |
| `INV_HEROES_002` | `HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION` | Active contract in WIP/RETURN_READY ⇒ all assigned heroes have status=ON_MISSION | `P1_004_InvariantVerificationTest` | P1 Critical |

### Economy (Non-Negative Balances)

| Doc ID | Code `InvariantId` | Description | P1 Test Coverage | Priority |
|--------|-------------------|-------------|------------------|----------|
| `INV_ECONOMY_001` | `ECONOMY__MONEY_NON_NEGATIVE` | `moneyCopper ≥ 0` | `P1_004_InvariantVerificationTest`, `P1_006_FeeEscrowTest` | P1 Critical |
| `INV_ECONOMY_002` | `ECONOMY__TROPHIES_NON_NEGATIVE` | `trophiesStock ≥ 0` | `P1_004_InvariantVerificationTest`, `P1_007_SellTrophiesTest` | P1 Critical |
| `INV_ECONOMY_003` | `ECONOMY__RESERVED_NON_NEGATIVE` | `reservedCopper ≥ 0` | `P1_006_FeeEscrowTest` | P1 Critical |
| `INV_ECONOMY_004` | `ECONOMY__AVAILABLE_NON_NEGATIVE` | `(moneyCopper - reservedCopper) ≥ 0` | `P1_006_FeeEscrowTest` | P1 Critical |

### Region (Bounded Metrics)

| Doc ID | Code `InvariantId` | Description | P1 Test Coverage | Priority |
|--------|-------------------|-------------|------------------|----------|
| `INV_REGION_001` | `REGION__STABILITY_0_100` | `stability ∈ [0, 100]` | `P1_004_InvariantVerificationTest` | P1 Critical |

### Guild (Bounded Metrics)

| Doc ID | Code `InvariantId` | Description | P1 Test Coverage | Priority |
|--------|-------------------|-------------|------------------|----------|
| `INV_GUILD_001` | `GUILD__REPUTATION_0_100` | `reputation ∈ [0, 100]` | `P1_004_InvariantVerificationTest` | P1 Critical |

---

## Priority Levels

- **P1 Critical**: Must never be violated. Violations indicate reducer logic bugs or data corruption.
- **P2 Important**: Should not be violated under normal conditions. May be temporarily violated during internal state transitions (not exposed to events).
- **P3 Nice-to-have**: Soft constraints for optimization or analytics.

---

## Test Coverage Summary

### Fully Covered (P1 tests exist)
- ✅ All ID monotonicity invariants (6/6)
- ✅ All contract state consistency invariants (5/5)
- ✅ All hero assignment invariants (2/2)
- ✅ All economy non-negative invariants (4/4)
- ✅ Region stability bounds (1/1)
- ✅ Guild reputation bounds (1/1)

### Coverage Gaps (none for PoC P1 scope)
- ✅ All critical PoC invariants have P1 test coverage

### Test Files by Invariant Category

| Category | Primary Test File | Secondary Coverage |
|----------|------------------|-------------------|
| IDs | `P1_012_GameStateInitializationTest`, `P1_004_InvariantVerificationTest` | — |
| Contracts | `P1_001_InvariantLockedBoardTest`, `P1_004_InvariantVerificationTest` | `P1_003_PoCScenarioTest` |
| Heroes | `P1_004_InvariantVerificationTest` | `P1_003_PoCScenarioTest` |
| Economy | `P1_004_InvariantVerificationTest`, `P1_006_FeeEscrowTest`, `P1_007_SellTrophiesTest` | `P1_003_PoCScenarioTest` |
| Region | `P1_004_InvariantVerificationTest` | `P1_010_StabilityUpdatedTest` |
| Guild | `P1_004_InvariantVerificationTest` | (not in P1 scope - guild progression is P2) |

---

## Verification Policy

**When**: Invariants are verified after **every** accepted command via `verifyInvariants(state)` in `step()` reducer.

**Output**: If violations detected, `InvariantViolated` events are emitted with:
- `invariantId: InvariantId` — machine-readable identifier
- `details: String` — human-readable diagnostic

**Golden Replay Contract**: All golden replays (GR1–GR3) must produce **zero** `InvariantViolated` events on happy paths.

---

## Notes

- Invariants are **structural** (state shape) and **semantic** (business logic).
- Violations are **always bugs** in reducer logic (never user input errors).
- Command validation (`canApply`) prevents violations **before** state mutation.
- `InvariantViolated` events are **defensive** checks for unexpected reducer bugs.
- In production, `InvariantViolated` should trigger alerts and safe-mode shutdown.

---

## Future Additions (Post-PoC)

| Proposed Invariant | Code Entry (TBD) | Description | Priority |
|-------------------|------------------|-------------|----------|
| `INV_TAX_001` | `TAX__DUE_DAY_FUTURE_OR_CURRENT` | `taxDueDay ≥ dayIndex` | P2 |
| `INV_TAX_002` | `TAX__PENALTY_NON_NEGATIVE` | `taxPenalty ≥ 0` | P2 |
| `INV_GUILD_002` | `GUILD__CONTRACTS_FOR_NEXT_RANK_POSITIVE` | `contractsForNextRank > 0` unless max rank | P2 |
| `INV_PROOF_001` | `PROOF__POLICY_VALID` | `proofPolicy ∈ {STRICT, LENIENT, OFF}` | P3 |
