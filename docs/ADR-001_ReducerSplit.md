# ADR-001: Reducer as Thin Orchestrator

**Status**: Accepted
**Date**: 2026-01-29
**Scope**: `core/Reducer.kt` architecture

---

## Context

`Reducer.kt` evolved into a ~1400 LOC monolith combining orchestration, domain logic, and day progression phases.
This harmed readability, ownership, and safe parallel work.

At the same time, the reducer is **architecturally sensitive**:

* deterministic RNG sequencing,
* strict event ordering,
* invariant enforcement.

Any refactor had to preserve these guarantees.

---

## Decision

### Reducer responsibilities (non-extractable)

`Reducer.kt` is reduced to a **pure orchestrator** and owns only:

* `step()` — single entry point
* command routing
* `SeqContext` and `assignSeq()` (event ordering)
* invariant verification
* revision increment

No domain logic remains inline.

---

### Handler-based extraction

All command and lifecycle logic is delegated to **internal handlers**, grouped by domain:

```
core/
├── Reducer.kt
└── handlers/
    ├── AdvanceDayHandler.kt
    ├── ContractHandlers.kt
    ├── EconomyHandlers.kt
    └── GovernanceHandlers.kt
```

Handler rules:

* `internal` visibility
* receive `SeqContext`
* return new state
* no direct event emission outside `ctx`

---

### AdvanceDay handling

`AdvanceDay` is extracted as a **single ordered pipeline** with fixed phases.
RNG draw order is documented and treated as a contract.

---

## Explicit Non-Decisions

* ❌ Extracting `SeqContext`
* ❌ Moving invariant checks out of `step()`
* ❌ Reflection/visitor-based event assignment

Reason: all increase nondeterminism risk.

---

## Consequences

**Positive**

* Reducer shrunk to ~250 LOC
* Clear domain ownership
* Isolated handler testing
* Determinism preserved

**Trade-offs**

* `AdvanceDayHandler` remains large
* Requires discipline around RNG usage and event emission

---

## Invariants Preserved

1. Determinism (same input + seed → same output)
2. Single event ordering source (`SeqContext`)
3. Single revision increment per `step()`
4. Invariants verified after every command

---

## Result

Reducer is a stable orchestration boundary;
all game logic lives in explicit, testable handlers.

---
