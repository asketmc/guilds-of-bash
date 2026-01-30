# ADR-002: Deterministic, Invariant-Driven Test Strategy

**Status**: Accepted
**Date**: 2026-01-29
**Scope**: `core-test`, reducer refactors, domain pipelines

---

## Context

The core engine relies on:

* deterministic RNG,
* strict event ordering,
* state + event invariants.

Refactors (especially reducer decomposition) repeatedly exposed bugs that were:

* not syntactic,
* not caught by type system,
* only visible via invariant violations across days.

A conventional unit-test-only approach was insufficient.

---

## Decision

### Tests are the **primary behavioral contract**

The system is guarded by a layered test strategy:

1. **Invariant-driven tests**

   * Global invariants are asserted after every `step()`
   * Any refactor must preserve all invariants

2. **Deterministic replay**

   * Same input + seed ⇒ identical state hash, event hash, RNG draw count
   * Used to detect hidden behavioral drift

3. **Golden scenarios**

   * End-to-end day progressions with fixed seeds
   * Protect against accidental semantic changes

4. **Targeted unit tests**

   * Applied to extracted models and handlers
   * Validate local rules without mocking global state

---

## Explicit Non-Decisions

* ❌ Snapshot tests without semantic meaning
* ❌ Randomized tests without fixed seeds
* ❌ Tests that assert implementation details instead of behavior

Reason: they hide drift and break during safe refactors.

---

## Consequences

**Positive**

* Refactors are safe by default
* Semantic bugs surface immediately
* Determinism violations are detectable
* Tests document domain rules implicitly

**Trade-offs**

* Tests are strict and sometimes verbose
* Refactors require understanding invariants
* Golden tests must be updated deliberately

---

## Invariants Enforced by Tests

* Determinism (replayable outcomes)
* Single event ordering source
* Valid hero lifecycle states
* Correct contract state transitions
* Conservation of money / escrow
* No illegal multi-assignment of heroes

---

## Result

Tests act as a **semantic safety net**, not a regression checklist.
Refactoring is allowed only if **all invariants and replays remain valid**.

---
