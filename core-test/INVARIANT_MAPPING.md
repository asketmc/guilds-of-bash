# Invariant Mapping: Doc IDs → Code → Coverage Claims

**Purpose**: Maps documentation invariant identifiers to code `InvariantId` enum entries and claimed P1 test coverage.

**Version**: 1.2
**Last Updated**: 2026-01-22

---

## Verification Status Legend

* **VERIFIED**: Confirmed by scanning repository tests for explicit assertions covering this invariant.
* **UNVERIFIED**: Not confirmed (names/claims may be outdated or incorrect).

---

## Invariant Categories

### Aggregate / Structural (Doc-level)

| Doc ID           | Code `InvariantId`                                                                    | Description                                                                                                                                                                                                                | Coverage (claimed)                | Verified   | Priority    |
| ---------------- | ------------------------------------------------------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------- | ---------- | ----------- |
| `INV_STRUCT_001` | *(no single enum entry; composite)*                                                   | IDs are unique within each relevant collection (inbox/board/active/roster/returns if applicable). *(Code has ID-monotonicity checks; explicit “unique within collection” aggregate is not represented as one enum entry.)* | TBD                               | UNVERIFIED | P1 Critical |
| `INV_STRUCT_002` | *(no single enum entry; composite)*                                                   | Cross-refs are valid (active→board, active→heroes, return→active, return→board). *(In code this is split across specific invariants; for PoC “return→active” must be interpreted carefully—see `INV_CONTRACTS_003`.)*      | TBD                               | UNVERIFIED | P1 Critical |
| `INV_STRUCT_003` | `CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE` *(partial coverage of doc aggregate)* | Board-active consistency: board status=LOCKED implies there exists at least one non-closed active referencing it. *(Doc text “exclusion/consistency” is broader than this single check.)*                                  | `P1_001_InvariantLockedBoardTest` | UNVERIFIED | P1 Critical |
| `INV_STRUCT_004` | *(no enum entry in provided `InvariantId`)*                                           | Collections keep the canonical sort order required for deterministic replays. *(Not represented as a code invariant id in the provided enum.)*                                                                             | TBD                               | UNVERIFIED | P1 Critical |

### IDs (Identity Monotonicity)

| Doc ID        | Code `InvariantId`                              | Description                             | Coverage (claimed)                 | Verified   | Priority    |
| ------------- | ----------------------------------------------- | --------------------------------------- | ---------------------------------- | ---------- | ----------- |
| `INV_IDS_001` | `IDS__NEXT_CONTRACT_ID_POSITIVE`                | `nextContractId > 0`                    | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_IDS_002` | `IDS__NEXT_ACTIVE_CONTRACT_ID_POSITIVE`         | `nextActiveContractId > 0`              | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_IDS_003` | `IDS__NEXT_HERO_ID_POSITIVE`                    | `nextHeroId > 0`                        | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_IDS_004` | `IDS__NEXT_CONTRACT_ID_GT_MAX_CONTRACT_ID`      | `nextContractId > max(inbox+board).id`  | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_IDS_005` | `IDS__NEXT_ACTIVE_CONTRACT_ID_GT_MAX_ACTIVE_ID` | `nextActiveContractId > max(active).id` | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_IDS_006` | `IDS__NEXT_HERO_ID_GT_MAX_HERO_ID`              | `nextHeroId > max(roster).id`           | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |

### Contracts (State Consistency)

| Doc ID              | Code `InvariantId`                                   | Description                                                                                                                                                                                                                                                                             | Coverage (claimed)                 | Verified   | Priority    |
| ------------------- | ---------------------------------------------------- | --------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ---------------------------------- | ---------- | ----------- |
| `INV_CONTRACTS_001` | `CONTRACTS__LOCKED_BOARD_HAS_NON_CLOSED_ACTIVE`      | Board status=LOCKED ⇒ at least 1 non-closed active references it. *(If you want “exactly 1”, that’s a stricter rule than the safe minimum.)*                                                                                                                                            | `P1_001_InvariantLockedBoardTest`  | UNVERIFIED | P1 Critical |
| `INV_CONTRACTS_002` | `CONTRACTS__RETURN_READY_HAS_RETURN_PACKET`          | Active status=RETURN_READY ⇒ exactly 1 return packet exists for this activeId *(and, by PoC intent, it should be the kind that requires CloseReturn).*                                                                                                                                  | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_CONTRACTS_003` | `CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE` | Return packet `activeId` references an existing active contract. *(PoC nuance: if your state retains “non-close” return packets as an audit trail, then this invariant should apply only to `requiresPlayerClose==true` packets; otherwise it will flag valid post-resolution states.)* | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_CONTRACTS_004` | `CONTRACTS__ACTIVE_DAYS_REMAINING_NON_NEGATIVE`      | `daysRemaining ≥ 0` for all active contracts                                                                                                                                                                                                                                            | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_CONTRACTS_005` | `CONTRACTS__WIP_DAYS_REMAINING_IN_1_2`               | Active status=WIP ⇒ `daysRemaining ∈ {1, 2}`                                                                                                                                                                                                                                            | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |

### Heroes (Assignment Consistency)

| Doc ID           | Code `InvariantId`                                          | Description                                                                       | Coverage (claimed)                 | Verified   | Priority    |
| ---------------- | ----------------------------------------------------------- | --------------------------------------------------------------------------------- | ---------------------------------- | ---------- | ----------- |
| `INV_HEROES_001` | `HEROES__ON_MISSION_IN_EXACTLY_ONE_ACTIVE_CONTRACT`         | Hero status=ON_MISSION appears in exactly 1 active among WIP/RETURN_READY         | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |
| `INV_HEROES_002` | `HEROES__ACTIVE_WIP_OR_RETURN_READY_HERO_STATUS_ON_MISSION` | Active in WIP/RETURN_READY ⇒ all assigned heroes exist and have status=ON_MISSION | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |

### Economy (Non-Negative Balances)

| Doc ID            | Code `InvariantId`                | Description                          | Coverage (claimed)                                            | Verified   | Priority    |
| ----------------- | --------------------------------- | ------------------------------------ | ------------------------------------------------------------- | ---------- | ----------- |
| `INV_ECONOMY_001` | `ECONOMY__MONEY_NON_NEGATIVE`     | `moneyCopper ≥ 0`                    | `P1_004_InvariantVerificationTest`, `P1_006_FeeEscrowTest`    | UNVERIFIED | P1 Critical |
| `INV_ECONOMY_002` | `ECONOMY__TROPHIES_NON_NEGATIVE`  | `trophiesStock ≥ 0`                  | `P1_004_InvariantVerificationTest`, `P1_007_SellTrophiesTest` | UNVERIFIED | P1 Critical |
| `INV_ECONOMY_003` | `ECONOMY__RESERVED_NON_NEGATIVE`  | `reservedCopper ≥ 0`                 | `P1_006_FeeEscrowTest`, `P1_023_CancelContractTest`           | UNVERIFIED | P1 Critical |
| `INV_ECONOMY_004` | `ECONOMY__AVAILABLE_NON_NEGATIVE` | `(moneyCopper - reservedCopper) ≥ 0` | `P1_006_FeeEscrowTest`                                        | UNVERIFIED | P1 Critical |

### Region (Bounded Metrics)

| Doc ID           | Code `InvariantId`        | Description            | Coverage (claimed)                 | Verified   | Priority    |
| ---------------- | ------------------------- | ---------------------- | ---------------------------------- | ---------- | ----------- |
| `INV_REGION_001` | `REGION__STABILITY_0_100` | `stability ∈ [0, 100]` | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |

### Guild (Bounded Metrics)

| Doc ID          | Code `InvariantId`        | Description             | Coverage (claimed)                 | Verified   | Priority    |
| --------------- | ------------------------- | ----------------------- | ---------------------------------- | ---------- | ----------- |
| `INV_GUILD_001` | `GUILD__REPUTATION_0_100` | `reputation ∈ [0, 100]` | `P1_004_InvariantVerificationTest` | UNVERIFIED | P1 Critical |

---

## Priority Levels

* **P1 Critical**: Must never be violated in externally observable states (post-`step`).
* **P2 Important**: Should not be violated; may be temporarily violated only if the state is not observable and is fully repaired before post-`step` validation.
* **P3 Nice-to-have**: Soft constraints.

---

## Coverage Summary (Claims Only)

All coverage statements in this document are currently **UNVERIFIED**.

---

## Verification Policy (Target)

**When**: Invariants are verified after every accepted command via `verifyInvariants(state)` in `step()` reducer.

**Output**: If violations are detected, `InvariantViolated` events are emitted with:

* `invariantId: InvariantId`
* `details: String`

**Golden Replay Contract**: Happy-path golden replays (GR1–GR3) should produce zero `InvariantViolated` events.

---

## Notes

* Invariants are post-state checks; they are not substitutes for command validation.
* `InvariantViolated` is a defensive signal; handling strategy (fail-fast vs telemetry-only) is environment/policy dependent.
* PoC nuance: if `ContractState.returns` contains “audit trail” packets that do **not** require player close (`requiresPlayerClose=false`), then `CONTRACTS__RETURN_PACKET_POINTS_TO_EXISTING_ACTIVE` should be scoped to packets that require close; otherwise valid post-resolution states will be flagged.

---

## Future Additions (Post-PoC)

| Proposed Invariant | Code Entry (TBD)                          | Description                                | Priority |
| ------------------ | ----------------------------------------- | ------------------------------------------ | -------- |
| `INV_TAX_001`      | `TAX__DUE_DAY_FUTURE_OR_CURRENT`          | `taxDueDay ≥ dayIndex`                     | P2       |
| `INV_TAX_002`      | `TAX__PENALTY_NON_NEGATIVE`               | `taxPenalty ≥ 0`                           | P2       |
| `INV_GUILD_002`    | `GUILD__CONTRACTS_FOR_NEXT_RANK_POSITIVE` | `contractsForNextRank > 0` unless max rank | P2       |
| `INV_PROOF_001`    | `PROOF__POLICY_VALID`                     | `proofPolicy ∈ {STRICT, LENIENT, OFF}`     | P3       |

---

## ASSUMPTIONS

* invariant_semantics_source=InvariantId_enum_and_current_step_behavior_observed_via_test_failures
* test_coverage_repository_scan_performed=false
* aggregate_invariants_in_enum=false
* verification_policy_section_is_target_not_actual=true
