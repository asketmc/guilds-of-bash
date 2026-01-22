# PoC Feature Manifest

**Purpose**: Formal decomposition of PoC functionality into feature groups with explicit I/O contracts (Commands → Events → State), dependencies, and test anchors.

**Version**: 1.0
**Last Updated**: 2026-01-21

---

## Feature Groups

| Feature ID | Description | Commands | Events | State Fields | Dependencies | Test Anchors |
|------------|-------------|----------|--------|--------------|--------------|--------------|
| **FG_01** | Day Progression | `AdvanceDay` | `DayStarted`, `InboxGenerated`, `HeroesArrived`, `WipAdvanced`, `ContractResolved`, `DayEnded` | `meta.dayIndex`, `contracts.inbox`, `heroes.roster`, `contracts.active[].daysRemaining` | — | `P1_003_PoCScenarioTest::poc scenario basic contract flow end to end` |
| **FG_02** | Contract Posting | `PostContract` | `ContractPosted` | `contracts.board`, `contracts.inbox`, `economy.reservedCopper` | FG_01 (inbox) | `P1_003_PoCScenarioTest::poc scenario with positive fee reduces money`, `P1_006_FeeEscrowTest` |
| **FG_03** | Contract Taking | (auto on `AdvanceDay`) | `ContractTaken`, `HeroDeclined` | `contracts.active`, `contracts.board[].status`, `heroes.roster[].status` | FG_01, FG_02 | `P1_003_PoCScenarioTest::poc scenario basic contract flow end to end` |
| **FG_04** | Contract Resolution | (auto on `AdvanceDay`) | `ContractResolved`, `TrophyTheftSuspected` | `contracts.returns`, `contracts.active[].status` | FG_03 | `P1_003_PoCScenarioTest::poc scenario basic contract flow end to end`, `P1_002_TrophyPipelineTest` |
| **FG_05** | Return Processing | `CloseReturn` | `ReturnClosed`, `StabilityUpdated` | `contracts.returns`, `economy.trophiesStock`, `economy.moneyCopper`, `economy.reservedCopper`, `region.stability` | FG_04 | `P1_003_PoCScenarioTest::happy path no invariant violations with fee escrow and trophy flow`, `P1_006_FeeEscrowTest` |
| **FG_06** | Trophy Sales | `SellTrophies` | `TrophySold` | `economy.trophiesStock`, `economy.moneyCopper` | FG_05 | `P1_007_SellTrophiesTest`, `P1_003_PoCScenarioTest` |
| **FG_07** | Command Validation | (all commands) | `CommandRejected` | (none - state unchanged on reject) | — | `P1_005_CommandValidationTest`, `P1_009_ReducerCriticalTest::command rejection leaves state unchanged` |
| **FG_08** | Invariant Verification | (all commands) | `InvariantViolated` | (all state) | — | `P1_004_InvariantVerificationTest`, `P1_001_InvariantLockedBoardTest` |
| **FG_09** | Fee Escrow | `PostContract`, `CloseReturn` | `ContractPosted`, `ReturnClosed` | `economy.reservedCopper`, `economy.moneyCopper` | FG_02, FG_05 | `P1_006_FeeEscrowTest` |
| **FG_10** | Tax System | `PayTax`, `AdvanceDay` | `TaxDue`, `TaxPaid`, `TaxMissed`, `GuildShutdown` | `meta.taxDueDay`, `meta.taxAmountDue`, `meta.taxPenalty`, `meta.taxMissedCount` | FG_01 | (P2 tests - not in P1 scope) |
| **FG_11** | Guild Progression | (auto on day end) | `GuildRankUp` | `guild.guildRank`, `guild.reputation`, `guild.completedContractsTotal`, `guild.contractsForNextRank` | FG_05 | (P2 tests - not in P1 scope) |
| **FG_12** | Proof Policy | `SetProofPolicy` | (none - command only) | (future field - not yet implemented) | — | (not tested in P1) |

---

## Command → Doc Mapping (Canonical Names)

**Note**: Documentation uses different names for some commands. This table provides bidirectional mapping.

| Code Name | Doc Name (if different) | Status |
|-----------|------------------------|--------|
| `PostContract` | `CreateContract` | ✅ Canonical: code |
| `CloseReturn` | `ProcessReturn` | ✅ Canonical: code |
| `SellTrophies` | `SellTrophiesToBroker` | ✅ Canonical: code |
| `AdvanceDay` | `AdvanceDay` | ✅ Same |
| `PayTax` | `PayTax` | ✅ Same |
| `SetProofPolicy` | `SetProofPolicy` | ✅ Same |

---

## Event → Doc Mapping

| Code Name | Doc Name (if different) | Status |
|-----------|------------------------|--------|
| `CommandRejected` | `InvalidCommandRejected` | ✅ Canonical: code |

Decision & field mapping

- Canonical runtime event: `CommandRejected` (code is authoritative).
- Doc alias: `InvalidCommandRejected` — retained only as a documentation alias to help readers who used the older doc name.

Canonical fields (runtime implementation)
- `cmdType: String` — runtime command class simple name (maps to doc `commandType`).
- `reason: RejectReason` — machine-readable error code (enum). Treat this as the doc's `code` value.
- `detail: String` — human-readable diagnostic (maps to doc `details`).
- `entityId` — NOT present in the runtime event. If callers need a structured entity reference, prefer a future explicit optional field added to the runtime event (and propagated to emit sites). For now, event authors may include identifying information inside `detail`.

Policy A Implementation: Command validation uses the runtime `CommandRejected` event with a `RejectReason` enum for machine-readable codes and a human-readable `detail` field. Documentation should reference the runtime field names and types; where external systems require the doc alias (`InvalidCommandRejected`), an adapter can map runtime fields to the alias (e.g., `reason.name` -> `code`, `detail` -> `details`, `cmdType` -> `commandType`, `entityId` -> null).

Migration guidance

- Documentation-first: keep the code canonical in documentation (as above). Update other docs and examples to use `CommandRejected` and the canonical field names.
- Adapter option: if an external consumer expects the `InvalidCommandRejected(code, details, commandType, entityId?)` shape, provide a small adapter/DTO that converts `CommandRejected` -> `InvalidCommandRejected` (map `reason.name()` to `code`, copy `detail` -> `details`, `cmdType` -> `commandType`, set `entityId` to null until runtime provides it).

---

## Metrics/Signals (Analyzable)

| Signal ID | Description | Source | Computed By | Output Location |
|-----------|-------------|--------|-------------|-----------------|
| **S7** | `ContractTakeRate` | `ContractPosted`, `ContractTaken` events | adapter-console | Console output after `DayEnded` |
| **S8** | `OutcomeCounts` | `ContractResolved.outcome` | adapter-console | Console output after `DayEnded` |
| **S9** | `MoneyΔDay` | `DaySnapshot.money` (prev vs current) | adapter-console | Console output after `DayEnded` |

---

## PoC Scope Boundaries

### In Scope (PoC MVP)
- Contract lifecycle: post → take → resolve → close → sell
- Fee escrow mechanics
- Command validation with rejection events
- Invariant verification
- Basic stability mechanics
- Deterministic RNG with reproducibility

### Out of Scope (Post-PoC)
- Tax system (implemented but not P1-tested)
- Guild rank progression (implemented but not P1-tested)
- Proof policy (command exists but no behavior)
- Hero traits/specialization beyond basic power
- Contract difficulty scaling beyond rank
- Multi-region support

---

## Golden Replays (GR)

See `P1_015_GoldenReplaysTest.kt` for formalized golden replay scenarios:

- **GR1**: Happy path (full contract lifecycle with SUCCESS outcome)
- **GR2**: Rejection scenarios (invalid commands, not found, insufficient money)
- **GR3**: Boundary cases (insufficient money/escrow edge cases)

---

## Notes

- All P1 tests use deterministic seeds for reproducibility
- State mutations only via `step(state, cmd, rng)` reducer
- Events are the ONLY output channel (no side effects)
- Invariants verified after each command
- RNG draws must be stable across refactorings (see `P1_019_RngDrawOrderGoldenTest.kt`)
