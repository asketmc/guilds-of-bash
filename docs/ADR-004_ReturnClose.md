## ADR-004: Explicit Return Closure Decision (`accept|reject`) to Prevent STRICT Deadlocks

**Status**: Accepted
**Date**: 2026-02-02
**Scope**: Core domain (commands/events/validation/handlers), console adapter, event serialization, testing guidance
**Owner**: Core

---

### Context

`ProofPolicy.STRICT` previously could create permanent deadlocks for `PARTIAL` returns requiring manual closure (`CloseReturn`), because closure could be blocked when proof was damaged or theft was suspected. This could leave:

* Return packet pending indefinitely
* Active contract stuck in non-terminal state
* Hero not returning to availability
* Escrow/reserved funds not released (state corruption / soft-lock)

The MVP goal is to guarantee that **manual returns are always terminable** under STRICT by requiring an explicit player decision.

---

### Decision

Introduce an explicit **player decision** for manual return closure:

* `accept`: trust the hero; close using existing payout/trophies logic
* `reject`: do not trust; close as FAIL-like (no payment, no trophies), but still terminate lifecycle and release escrow

This is implemented as:

* A `ReturnDecision` enum `{ ACCEPT, REJECT }`
* `CloseReturn` accepts an optional `decision: ReturnDecision?`

    * `null` defaults to `ACCEPT` for legacy compatibility under non-STRICT policies
    * Under `STRICT`, `null` is invalid and must be rejected by validation
* A new event `ReturnRejected(activeContractId)` emitted when the player rejects

Determinism is preserved: **no RNG draws** are performed by either `accept` or `reject` close paths.

---

### Drivers

* **Gameplay safety**: eliminate soft-locks and stuck state under STRICT
* **Deterministic architecture**: no hidden RNG draws; stable replayability
* **Minimal MVP surface**: avoid disputes/arbitration/reputation systems in this ticket
* **Observability**: make rejection explicit in the event stream

---

### Considered Options

1. **Keep STRICT blocking closure**
   Rejected: allows permanent deadlocks with no exit path.

2. **Auto-timeout / forced auto-resolution**
   Rejected for MVP: adds new lifecycle timers and balancing surface.

3. **Separate `CloseReturnDecision` command**
   Not chosen: increases API surface; optional decision on existing command is cheaper for MVP.

4. **Explicit `accept|reject` decision on `CloseReturn` (chosen)**
   Provides a deterministic exit path, minimal domain changes, and preserves existing accept behavior.

---

### Consequences

**Positive**

* STRICT no longer bricks gameplay: returns are always closable via `accept` or `reject`.
* `reject` is deterministic and does not depend on money availability for fee payment.
* Event stream explicitly records rejection (`ReturnRejected`), improving audit/replay clarity.

**Trade-offs**

* Command signature change may require updating call sites and golden replays.
* Strict closure semantics become: “requires decision” rather than “silent block.”

---

### Implementation Summary (MVP)

#### Core Domain

* **Commands**

    * Added `ReturnDecision { ACCEPT, REJECT }`
    * Modified `CloseReturn` to accept `decision: ReturnDecision?`
* **Events**

    * Added `ReturnRejected(activeContractId)`
* **Policy**

    * `ReturnClosurePolicy.canClose(policy, quality, suspectedTheft, decision)`

        * STRICT: requires `decision != null` (`strict_policy_requires_decision`)
        * `REJECT` always allowed (unblocks gameplay)
        * `ACCEPT` may still be denied under STRICT if damaged/suspected theft (policy gate)
* **Validation**

    * STRICT requires explicit decision
    * `REJECT` bypasses fee-payment money validation
    * `ACCEPT` retains existing money checks
* **Handlers**

    * `handleCloseReturn` updated to support:

        * `REJECT`: no fee payment, no trophies, release escrow, no completion increment (FAIL-like), emit `ReturnRejected`
        * `ACCEPT`: unchanged legacy behavior, emit `ReturnClosed`
    * Both branches: **zero RNG draws**
* **Serialization**

    * Canonical event JSON updated to include `ReturnRejected`
* **Reducer**

    * Event sequencing updated to include `ReturnRejected`

#### Console Adapter

* `close <activeId> [accept|reject]`
* Validates decision keyword
* Passes `ReturnDecision` to `CloseReturn`
* Logs `ReturnRejected`

#### Documentation

* `TESTING.md` updated with MVP change summary and determinism note

---

### Acceptance Criteria

* ✅ Under STRICT, returns are always closable via `close <id> accept` or `close <id> reject`
* ✅ `ACCEPT` matches existing manual close behavior
* ✅ `REJECT` terminates lifecycle (return removed, active closed, hero available)
* ✅ `REJECT` does not pay fee
* ✅ `REJECT` releases reserved/escrow funds (no stuck reserved)
* ✅ `REJECT` grants 0 trophies
* ✅ No RNG draws occur during close commands
* ✅ Determinism preserved: same seed + commands → same events, same `rngDraws`

---

### Files Modified

**Core**

* `core/src/main/kotlin/core/Commands.kt`
* `core/src/main/kotlin/core/Events.kt`
* `core/src/main/kotlin/core/Reducer.kt`
* `core/src/main/kotlin/core/pipeline/ReturnClosurePolicy.kt`
* `core/src/main/kotlin/core/handlers/ContractHandlers.kt`
* `core/src/main/kotlin/core/CommandValidation.kt`
* `core/src/main/kotlin/core/serde/CanonicalEventsJson.kt`

**Adapter**

* `adapter-console/src/main/kotlin/console/Main.kt`
* `adapter-console/src/main/kotlin/console/DiegeticText.kt`

**Docs**

* `core-test/TESTING.md`

---

## Proposed Unit Tests (Coverage for New Contracts + Invariants)

### A) Validation / Policy

1. **STRICT requires explicit decision**

* Setup: STRICT policy + return requiring player close
* Action: `CloseReturn(activeId, decision=null)`
* Expect: `CommandRejected` with reason `strict_policy_requires_decision`

2. **REJECT bypasses fee money validation**

* Setup: return requiring close, `moneyCopper < fee`
* Action: `CloseReturn(activeId, REJECT)`
* Expect: command accepted; lifecycle terminates; no payment attempted

3. **ACCEPT retains money validation**

* Setup: `moneyCopper < fee`
* Action: `CloseReturn(activeId, ACCEPT)`
* Expect: `CommandRejected` (existing insufficient funds semantics)

### B) State Transitions (Lifecycle Termination)

4. **REJECT terminates lifecycle**

* Expect after step:

    * return removed
    * active contract CLOSED
    * hero AVAILABLE

5. **ACCEPT terminates lifecycle (baseline parity)**

* Same assertions as existing tests for legacy manual close

### C) Economy / Escrow

6. **REJECT releases escrow / reserved**

* Setup: reserved includes escrow for that return (per your actual contract semantics)
* Expect: reserved decreases by the escrow amount; never negative

7. **REJECT does not pay fee**

* Expect: `moneyCopper` unchanged by fee (delta 0)

8. **REJECT awards 0 trophies**

* Expect: `trophiesStock` unchanged (delta 0)

### D) Progression / Counters

9. **REJECT does not increment completion counters**

* Expect: `completedContractsTotal` unchanged (FAIL-like)

10. **ACCEPT preserves existing progression behavior**

* Expect: same increments as legacy manual close (if applicable)

### E) Events / Sequencing / Serialization

11. **ReturnRejected event emitted on REJECT**

* Expect: `ReturnRejected(activeId)` present, `seq` assigned correctly

12. **No `ReturnRejected` on ACCEPT**

* Expect: only `ReturnClosed` (and existing events)

13. **Canonical JSON includes ReturnRejected**

* Serialize event stream including rejection; assert JSON schema field presence and stability

### F) Determinism Contract

14. **No RNG draws on close**

* Setup: record `rngDraws` before/after `CloseReturn(ACCEPT)` and `CloseReturn(REJECT)`
* Expect: unchanged draws in both cases

15. **Replay stability**

* Same seed + same command sequence with close decisions → identical events hash and state hash

### G) Invariant Regression Tests (targeted)

16. **No stuck reserved due to rejected return**

* After REJECT, assert reserved does not retain escrow tied to that return (by delta or by aggregate checks)

17. **Contract collections consistency**

* After REJECT/ACCEPT: contract not simultaneously present in `active` and `returns` and any terminal collection (as per your lifecycle rules)

---
