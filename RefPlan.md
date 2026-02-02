# Step-by-Step Refactoring Plan (Inspection-Driven)

> **Baseline:** 610 inspection warnings across multiple categories  
> **Target:** Reduce to ~61 warnings maximum  
> **Rule:** No semantic changes without tests passing  
> **Strategy:** Incremental, CI-safe refactor with detailed task breakdown for smaller AI agents

## Inspection Summary
- **Line Separators:** 7 files (gradlew, *.md, ReasonTag.kt)
- **Redundant Suppressions:** 9 files with 15+ redundant annotations
- **Test Methods Without Assertions:** 47 test methods across 12 test classes
- **Unused Parameters:** 15 methods with unused parameters
- **Unused Declarations:** 50+ unused symbols (classes, functions, properties)
- **Dependency Issues:** 25 classes with too many dependencies (>10)
- **Package Issues:** 13 packages with structural problems
- **Print/Println Usage:** 200+ occurrences across test and main code
- **Probable Bugs:** 8 instances of assigned-never-read variables

---

## Phase 0 — Baseline & Safety
- [x] Ensure green CI on current `dev`
- [x] Freeze behavior: no feature changes during refactor
- [x] Enable auto-format on save (IDEA) but **do not apply globally yet**

---

## Phase 1 — Mechanical Hygiene (Zero-Risk)

### 1. Line Separators (7 files)
**Goal:** Single project-wide standard (\r\n)
**Files to fix:**
- [ ] `gradlew` - Unix line endings (\n) → Windows (\r\n)
- [ ] `guidelines.md` - Unix → Windows
- [ ] `product.md` - Unix → Windows  
- [ ] `ReasonTag.kt` - Unix → Windows
- [ ] `structure.md` - Unix → Windows
- [ ] `tech.md` - Unix → Windows
- [ ] `validated-design-doc.md` - Unix → Windows

**Task for AI agent:**
```
For each file listed above:
1. Read file content
2. Replace all \n with \r\n
3. Write back to file
4. Verify .editorconfig enforces end_of_line = crlf
```
**Commit:** `chore: normalize line separators to CRLF`

---

### 2. Redundant Suppressions (9 files, 15+ annotations)
**Goal:** Remove noise, keep intent
**Files with redundant suppressions:**
- [x] `AdvanceDayHandler.kt` - 2 redundant suppressions
- [x] `ContractHandlers.kt` - 5 redundant suppressions  
- [x] `DiegeticStatus` - 1 redundant suppression
- [x] `EconomyHandlers.kt` - 2 redundant suppressions
- [x] `GovernanceHandlers.kt` - 1 redundant suppression
- [x] `RankThreshold.kt` - 1 redundant suppression
- [ ] `CommandValidation.kt` - 7 ReturnCount suppressions (review if still needed)
- [ ] `EventFactory` - 1 UNCHECKED_CAST suppression

**Task for AI agent:**
```
For each remaining file:
1. Identify @Suppress annotations
2. Check if warning still fires without suppression
3. If no warning: remove suppression
4. If warning exists but parameter unused: rename param to _paramName
5. Keep suppression only if architecturally justified
```
**Commit:** `chore: remove redundant suppressions`

---

### 3. Unused Imports (11 files)
**Goal:** Clean compiler surface
**Files with unused imports:**
- [ ] `Commands.kt` - 2 unused imports
- [ ] `CommandValidation.kt` - 1 unused import
- [ ] `EconomyHandlers.kt` - 1 unused import
- [ ] `EdgeCasesPoCTest.kt` - 1 unused import
- [ ] `GoldenReplaysTest.kt` - 1 unused import
- [x] `GovernanceHandlers.kt` - 1 unused import (completed)
- [ ] `HashingTest.kt` - 1 unused import
- [ ] `Main.kt` - 3 unused imports
- [ ] `RankThreshold.kt` - 1 unused import
- [ ] `Reducer.kt` - 1 unused import
- [ ] `ReducerCriticalTest.kt` - 1 unused import
- [ ] `StabilityUpdatedTest.kt` - 1 unused import

**Task for AI agent:**
```
For each file:
1. Read file and identify unused import statements
2. Remove unused imports
3. Verify code still compiles
4. Keep imports that are used in KDoc or annotations
```
**Commit:** `chore: remove unused imports`

---

### 4. Local var → val Conversion (6 files)
**Goal:** Immutability where possible
**Files with mutable vars that can be vals:**
- [ ] `AbuseTechnicalPoCTest` - 1 var → val
- [ ] `CancelContractTest` - 4 vars → vals
- [ ] `GoldenReplaysTest` - 1 var → val

**Task for AI agent:**
```
For each flagged variable:
1. Verify variable is never reassigned after initialization
2. Change var to val
3. Ensure tests still pass
```
**Commit:** `refactor: convert immutable vars to vals`

---

## Phase 2 — Test Semantics & Intent

### 5. Tests Without Assertions (47 test methods)
**Goal:** Make test intent explicit

**AbuseTechnicalPoCTest (8 methods):**
- [ ] `AB_POC_REPROCESS_DUP same cmdId cannot be reprocessed()` - Add assertion that reprocessing fails
- [ ] `AB_POC_SELL_BOUNDARY sell with negative amount()` - Add assertion about behavior
- [ ] `AB_POC_INVARIANT_ENFORCEMENT invariant violations are detected and reported()` - Add assertion checking violation events
- [ ] 5 other methods - Add appropriate assertions

**InvariantVerificationTest (6 methods):**
- [ ] `economy negatives()` - Add assertion that violations are detected
- [ ] `bounds for stability and reputation()` - Add bounds checking assertions
- [ ] `id counters must be positive()` - Add positive ID assertions
- [ ] `nextContractId must exceed max contractId in inbox+board()` - Add ID ordering assertions
- [ ] `active daysRemaining constraints()` - Add constraint assertions
- [ ] `allows informational return packet without active after auto-close()` - Add state assertions

**GoldenReplaysTest (2 methods):**
- [ ] `golden replay RNG draw count is stable()` - Add assertion comparing draw counts
- [ ] Add hash stability assertions

**Other Test Classes (31 methods):**
- [ ] `CorePerfLoadTest.perf - AdvanceDay baseline vs one-quest-per-day vs post-all()` - Add performance assertions
- [ ] `HashingTest.hashState reflects arrivalsToday changes()` - Add hash comparison assertions
- [ ] `InvariantsAfterEachStepTest` (2 methods) - Add invariant checking assertions
- [ ] `LockedBoardInvariantTest` (5 methods) - Add board state assertions
- [ ] `PoCManifestCoverageTest` (3 methods) - Add coverage assertions
- [ ] `RngDrawOrderGoldenTest.RNG draw count per command is documented()` - Add documentation assertions
- [ ] `RngSeedFinder.find seeds that produce GOOD NEUTRAL BAD buckets()` - Add bucket assertions
- [ ] `TrophyPipelineTest.end to end trophy flow from resolve to sell()` - Add pipeline assertions

**Task for AI agent (per test class):**
```
1. Analyze test method purpose from name and body
2. Determine appropriate assertion type:
   - State verification: assertEquals, assertTrue
   - Event verification: assertEventPresent, assertEventCount
   - Invariant verification: assertNoViolations
   - Hash/Golden: assertEquals with expected hash
3. Add minimal assertion that verifies test intent
4. If test is procedural by design, add comment explaining why
```
**Commit per test class:** `test: add assertions to [TestClassName]`

---

### 6. Constant Parameters in Tests (Multiple files)
**Goal:** Reduce false variability

**Files with constant parameters:**
- [ ] `GoldenOutputTest` - fee=500, rank=Rank.C always
- [ ] `InvariantAssertions.kt` - seed=42 always
- [ ] `OutcomeBranchesTest` - stateSeed=42 always
- [ ] `PerfHelpers.kt` - stateSeed=42, rngSeed=100L always
- [ ] `TestApi.kt` - stateSeed=42 always
- [ ] `TrophyPipelineHelpers.kt` - 10 constant parameters always same values

**Task for AI agent:**
```
For each file with constant parameters:
1. Identify methods with parameters that always receive same value
2. Options:
   - Replace with named constant: private const val DEFAULT_SEED = 42
   - Inline the literal with comment: /* deterministic seed */ 42
   - Create parameterized test if variation intended
3. Update all call sites
4. Verify tests still pass
```
**Commit:** `test: normalize constant parameters`

---

## Phase 3 — API & Signature Integrity

### 7. Unused Parameters (15 methods)
**Goal:** Align signatures with usage

**Handler Methods (already partially completed):**
- [x] `AdvanceDayHandler.phasePickup` - rng → _rng
- [x] `ContractHandlers` - 5 methods, rng → _rng
- [x] `EconomyHandlers` - 2 methods, rng → _rng  
- [x] `GovernanceHandlers.handleSetProofPolicy` - rng → _rng
- [x] `DiegeticStatus.render` - rng → _rng
- [x] `RankThreshold.calculateNextRank` - currentRankOrdinal → _currentRankOrdinal

**Remaining unused parameters:**
- [ ] `CommandValidation.validateCreateContract` - state parameter unused
- [ ] `EconomySettlement.computeAutoCloseDelta` - currentEconomy parameter unused
- [ ] `EconomySettlement.computeManualCloseDelta` - currentEconomy parameter unused
- [ ] `EventFactory.buildArgs` - params parameter unused
- [ ] `TrophyPipelineHelpers.assertSellAllApplied` - events parameter unused
- [ ] `TrophyPipelineHelpers.trophyE2EFixture` - heroRank parameter unused

**Task for AI agent:**
```
For each remaining unused parameter:
1. Check if parameter is required by interface/signature contract
2. If required by interface: rename to _parameterName
3. If not required: remove parameter and update all call sites
4. If future extension point: add KDoc explaining purpose
5. Verify all tests pass after changes
```
**Commit per file:** `refactor: handle unused parameters in [FileName]`

---

### 8. Always-True/Always-False Conditions (4 instances)
**Goal:** Eliminate dead logic

**Specific instances:**
- [ ] `InvariantVerifier.kt` - "Condition is always false"
- [ ] `RngDrawOrderGoldenTest.kt` - "Condition is always true"
- [ ] `RngSeedFinder.kt` - 2 instances of "Condition is always false"

**Task for AI agent:**
```
For each constant condition:
1. Locate the specific condition in the file
2. Analyze why condition is constant:
   - Dead code branch?
   - Placeholder for future logic?
   - Bug in condition logic?
3. Options:
   - Remove dead branch entirely
   - Replace with explicit assertion if invariant
   - Fix condition logic if bug
   - Add TODO comment if placeholder
4. Add test to verify correct behavior
```
**Commit:** `refactor: remove constant conditions`

---

### 9. Null Dereference Issues (13 instances)
**Goal:** Fix potential null pointer exceptions

**Files with null dereference warnings:**
- [ ] `ContractPickupModel` - 1 possible null dereference
- [ ] `EventFactory` - 1 possible null dereference
- [ ] `OutcomeBranchesTest` - 5 possible null dereferences
- [ ] `StewardReportRenderer` - 8 possible null dereferences

**Task for AI agent:**
```
For each null dereference warning:
1. Locate the specific line with potential null access
2. Add null safety:
   - Use safe call operator: obj?.method()
   - Add null check: if (obj != null) { obj.method() }
   - Use elvis operator: obj ?: defaultValue
3. Verify fix doesn't change behavior
4. Add test case for null scenario if applicable
```
**Commit per file:** `fix: add null safety to [FileName]`

---

## Phase 4 — Dependency Pressure Reduction

### 10. Classes with Too Many Dependencies (25 classes >10 deps)
**Goal:** Enforce stability gradient

**Test Classes (can be more lenient but still improve):**
- [ ] `AbuseTechnicalPoCTest` (19 deps) - Extract test fixtures
- [ ] `CancelContractTest` (20 deps) - Extract contract test helpers
- [ ] `EdgeCasesPoCTest` (29 deps) - Extract edge case fixtures
- [ ] `GoldenOutputTest` (38 deps) - Extract rendering test helpers
- [ ] `HashingTest` (27 deps) - Extract hashing test utilities
- [ ] `InvariantVerificationTest` (24 deps) - Extract invariant test helpers
- [ ] `OutcomeBranchesTest` (24 deps) - Extract outcome test fixtures
- [ ] `SerializationTest` (27 deps) - Extract serialization test helpers

**Production Classes (higher priority):**
- [ ] `ContractPickupModel` (13 deps) - Extract pickup logic helpers
- [ ] `DiegeticStatus` (12 deps) - Extract status rendering helpers
- [ ] `RenderContext` (11 deps) - Extract context utilities
- [ ] `StewardReportRenderer` (14 deps) - Extract report formatting helpers
- [ ] `TestStateFactory` (21 deps) - Extract state building helpers

**Task for AI agent (per class):**
```
1. Analyze class dependencies and identify clusters:
   - Data transformation helpers
   - Rendering utilities  
   - Test fixtures
   - Domain logic helpers
2. Extract helper classes/objects for each cluster
3. Move related methods to extracted helpers
4. Update original class to use helpers
5. Verify dependency count reduced to ≤10
6. Ensure all tests pass
```
**Commit per class:** `refactor: extract helpers from [ClassName]`

---

### 11. Classes with Too Many Dependents (23 classes >10 dependents)
**Goal:** Reduce blast radius of changes

**High-impact core classes:**
- [ ] `GameState` (40 dependents) - Consider state access patterns
- [ ] `Event` (35 dependents) - Consider event type grouping
- [ ] `Rng` (34 dependents) - Consider RNG interface extraction
- [ ] `SalvagePolicy` (32 dependents) - Consider policy value object
- [ ] `ContractState` (27 dependents) - Consider contract state splitting
- [ ] `Rank` (23 dependents) - Consider rank value object
- [ ] `Outcome` (22 dependents) - Consider outcome grouping
- [ ] `StepResult` (22 dependents) - Consider result type refinement
- [ ] `EconomyState` (21 dependents) - Consider economy state splitting

**Task for AI agent (per high-impact class):**
```
1. Analyze why class has many dependents:
   - Central data structure?
   - God object with multiple responsibilities?
   - Missing abstraction layer?
2. Consider splitting strategies:
   - Extract interfaces for different use cases
   - Split by responsibility (read vs write)
   - Create facade for complex interactions
3. Implement gradual migration:
   - Create new interfaces/classes
   - Migrate dependents incrementally
   - Keep original as facade if needed
4. Verify no behavior changes
```
**Commit per refactoring:** `refactor: reduce dependents of [ClassName]`

---

## Phase 5 — Packaging & Modularity

### 12. Missing package-info.java (13 packages)
**Goal:** Document module boundaries

**Packages needing package-info.java:**
- [ ] `console` - Console adapter interfaces
- [ ] `console.render` - Rendering utilities
- [ ] `core` - Core game logic
- [ ] `core.flavour` - Game content and flavor text
- [ ] `core.handlers` - Command handlers
- [ ] `core.hash` - State and event hashing
- [ ] `core.invariants` - State validation rules
- [ ] `core.partial` - Partial outcome resolution
- [ ] `core.pipeline` - Game processing pipeline
- [ ] `core.primitives` - Basic game types
- [ ] `core.rng` - Random number generation
- [ ] `core.serde` - Serialization utilities
- [ ] `core.state` - Game state data structures
- [ ] `test` - Test utilities
- [ ] `test.helpers` - Test helper functions

**Task for AI agent:**
```
For each package:
1. Create package-info.java file
2. Add minimal Javadoc describing:
   - Package purpose
   - Key responsibilities
   - Main entry points
   - Dependencies/relationships
3. Example template:
   /**
    * [Package purpose in one sentence]
    * 
    * <p>Key components:
    * <ul>
    * <li>[Component 1] - [responsibility]
    * <li>[Component 2] - [responsibility]
    * </ul>
    */
   package [package.name];
```
**Commit:** `docs: add package-info.java files`

---

### 13. Package Structure Issues
**Goal:** Reflect real dependency graph

**Packages with disjoint dependency graphs (can be split):**
- [ ] `console` - Can be decomposed into 7 independent packages
- [ ] `core` - Can be decomposed into 7 independent packages  
- [ ] `core.flavour` - Can be decomposed into 2 independent packages
- [ ] `core.pipeline` - Can be decomposed into 15 independent packages
- [ ] `core.primitives` - Can be decomposed into 13 independent packages
- [ ] `test` - Can be decomposed into 36 independent packages
- [ ] `test.helpers` - Can be decomposed into 9 independent packages

**Packages with cross-module issues:**
- [ ] `console` - Has classes in both adapter-console.main and adapter-console.test
- [ ] `core.partial` - Has classes in both core.main and core-test.test

**Task for AI agent (per package):**
```
1. Analyze package dependency graph
2. Identify independent clusters of classes
3. Create new sub-packages for each cluster:
   - core.primitives.contracts
   - core.primitives.heroes
   - core.primitives.economy
   - etc.
4. Move classes to appropriate sub-packages
5. Update import statements
6. Verify no circular dependencies
```
**Commit per package:** `refactor: restructure [package.name]`

---

### 14. Module Size Issues
**Goal:** Restore architectural signal

**Module too large:**
- [ ] `Guilds-of-Bash.core.main` (157 classes > 100) - Split into focused modules

**Module too small:**
- [ ] `Guilds-of-Bash.adapter-console.test` (9 classes < 10) - Consider merging or justifying

**Task for AI agent:**
```
For core.main module split:
1. Analyze 157 classes and group by responsibility:
   - Domain entities (GameState, Hero, Contract, etc.)
   - Business logic (handlers, pipeline)
   - Infrastructure (serialization, hashing, RNG)
   - Utilities (flavour, invariants)
2. Create new modules:
   - core.domain - Core entities and state
   - core.logic - Command handlers and pipeline
   - core.infrastructure - Serialization, hashing, RNG
3. Move classes to appropriate modules
4. Update build.gradle dependencies
5. Verify module boundaries are clean
```
**Commit:** `refactor: split core.main into focused modules`

---

## Phase 6 — Logging & Diagnostics

### 15. Print/Println Usage (200+ occurrences)
**Goal:** Deterministic, suppressible output

**Files with heavy println usage:**
- [ ] `Main.kt` (80+ occurrences) - Replace with proper logging
- [ ] `AbuseTechnicalPoCTest` (17 occurrences) - Replace with test output capture
- [ ] `EventAssertions.kt` (16 occurrences) - Replace with assertion messages
- [ ] `InvariantsAfterEachStepTest` (10 occurrences) - Replace with test logging
- [ ] `CorePerfLoadTest` (2 occurrences) - Replace with performance logging
- [ ] `OutcomeBranchesTest` (2 occurrences) - Replace with test assertions
- [ ] `PerfHelpers.kt` (1 occurrence) - Replace with performance metrics
- [ ] `PoCManifestCoverageTest` (11 occurrences) - Replace with test reporting
- [ ] `RngDrawOrderGoldenTest` (11 occurrences) - Replace with test verification
- [ ] `RngSeedFinder` (8 occurrences) - Replace with structured output
- [ ] `TrophyPipelineHelpers.kt` (2 occurrences) - Replace with test utilities

**Task for AI agent (per file):**
```
1. Categorize println usage:
   - Debug output → Remove or convert to logger.debug()
   - User interaction (Main.kt) → Keep but consider structured logging
   - Test diagnostics → Convert to test output or assertions
   - Performance metrics → Convert to structured metrics
2. Replacement strategies:
   - Main.kt: Use java.util.logging or simple System.out with structured format
   - Test files: Use test output capture or convert to assertions
   - Debug files: Remove or use conditional debug logging
3. Ensure no behavior changes in user-facing output
4. Verify tests still pass and provide useful output
```
**Commit per file type:** `refactor: replace println in [file-category]`

---

## Phase 7 — Dead Code & Spec Drift

### 16. Unused Declarations (50+ symbols)
**Goal:** Remove misleading surface

**Unused Classes/Objects:**
- [ ] `ReasonTag` - Entire class and all enum values unused
- [ ] `P3` - Entire class unused
- [ ] `ContractCardRenderer` - Object and function unused
- [ ] `HeroQuotes` - Class unused
- [ ] `NarrativePhase` - Class unused
- [ ] `Perf` - Class unused
- [ ] `Smoke` - Class unused
- [ ] `SmokeSuite` - Class unused
- [ ] `StoryTag` - Class unused

**Unused Properties:**
- [ ] `BalanceSettings.DEFAULT_N_INBOX` - Never used
- [ ] `BalanceSettings.DEFAULT_N_HEROES` - Never used

**Unused Functions:**
- [ ] `BoxRenderer.ensureNoOverflow` - Never used
- [ ] `CanonicalJson.deserialize` - Never used
- [ ] `ContractAttractiveness.getDifficultyFromDraft` - Never used
- [ ] `ContractAttractiveness.estimateDifficultyFromBoard` - Never used
- [ ] `HeroLifecycle.markOnMission` - Never used
- [ ] `StabilityModel.computeAutoResolvePenalty` - Never used
- [ ] `TrophiesQuality.toCoreQuality` - Never used
- [ ] Multiple test helper functions in `EventAssertions.kt`, `InvariantAssertions.kt`, `SealedReflectionAssertions.kt`, `TestApi.kt`

**Task for AI agent (per category):**
```
1. Verify symbol is truly unused (check all references)
2. Determine if symbol represents:
   - Dead code → Delete
   - Future extension point → Document with TODO and keep
   - Test utility that should be in test helpers → Move
3. For deletions:
   - Remove symbol definition
   - Remove any imports of the symbol
   - Verify code still compiles
4. For moves to test helpers:
   - Move to appropriate test helper class
   - Update any existing usages
```
**Commit per category:** `refactor: remove unused [category] declarations`

---

### 17. Probable Bugs (8 instances)
**Goal:** Correctness before cleanup

**Assigned-but-never-read variables:**
- [ ] `AbuseTechnicalPoCTest` - 4 instances of assigned values never read
- [ ] `OutcomeBranchesTest` - 2 instances of assigned values never read  
- [ ] `StabilityUpdatedTest` - 1 instance of assigned value never read
- [ ] `HashingTest` - 1 unused variable
- [ ] `Main.kt` - 1 unused variable
- [ ] `SellTrophiesTest` - 1 unused variable

**Other probable bugs:**
- [ ] `GoldenReplaysTest` - Condition 'sold >= 0' is always true
- [ ] `ContractPickupModel` - Range with start greater than endInclusive is empty
- [ ] `CancelContractTest` - 2 redundant conversion method calls

**Task for AI agent (per bug):**
```
1. Locate the specific problematic code
2. Analyze the issue:
   - Assigned-never-read: Remove assignment or add usage
   - Always-true condition: Remove or fix condition logic
   - Empty range: Fix range bounds or handle empty case
   - Redundant conversion: Remove redundant call
3. Add test case to verify fix
4. Ensure fix doesn't change intended behavior
```
**Commit per bug:** `fix: [specific-bug-description]`

---

## Phase 8 — Kotlin Modernization

### 18. Kotlin 1.9+ Idioms (2 files)
**Goal:** Use modern Kotlin features

**Files using deprecated Enum.values():**
- [ ] `TaxCalculation.kt` - 2 instances of `Enum.values()` → `Enum.entries`

**Task for AI agent:**
```
1. Locate all usages of Enum.values()
2. Replace with Enum.entries
3. Verify behavior is identical (entries is same as values().toList())
4. Run tests to ensure no regressions
5. Check if any other Kotlin 1.9+ features can be adopted
```
**Commit:** `chore: adopt Kotlin 1.9 idioms`

---

## Phase 9 — Finalization

### 19. Global Re-inspection
**Goal:** Verify improvement and no regressions

**Tasks:**
- [ ] Re-run full IDEA inspection
- [ ] Compare before/after warning counts:
  - **Before:** 610 total warnings
  - **Target:** ≤61 warnings (90% reduction)
- [ ] Categorize remaining warnings:
  - Acceptable architectural decisions
  - Future improvement opportunities  
  - False positives
- [ ] Ensure no new warnings introduced
- [ ] Verify all tests still pass
- [ ] Check CI pipeline is green

**Task for AI agent:**
```
1. Run inspection and generate new report
2. Compare with baseline numbers
3. Document any remaining warnings with justification
4. Verify test suite passes completely
5. Check that build artifacts are identical in behavior
```
**Commit:** `chore: final inspection cleanup`

---

### 20. Documentation Updates
**Goal:** Reflect architectural improvements

**Files to update:**
- [ ] `tech.md` - Update with any architectural changes
- [ ] `structure.md` - Update package/module structure if changed
- [ ] `RefPlan.md` - Mark all completed tasks
- [ ] Add ADR (Architecture Decision Record) if significant changes made

**Task for AI agent:**
```
1. Review all changes made during refactoring
2. Identify any architectural impacts
3. Update documentation to reflect current state
4. Add migration notes if interfaces changed
5. Document any new patterns or conventions established
```
**Commit:** `docs: update architecture documentation`

---

## Success Metrics

**Quantitative Goals:**
- [x] **Baseline:** 610 inspection warnings
- [ ] **Target:** ≤61 warnings (90% reduction)
- [ ] **Test Coverage:** Maintain or improve current coverage
- [ ] **Build Time:** No significant regression
- [ ] **CI Pipeline:** All checks pass

**Qualitative Goals:**
- [ ] **Code Clarity:** Explicit test intentions, clear naming
- [ ] **Dependency Health:** Reduced coupling, clearer boundaries
- [ ] **Maintainability:** Easier to understand and modify
- [ ] **Architecture:** Better package structure, cleaner modules

**Deliverables:**
- Clean inspection report with documented remaining warnings
- Explicit test assertions showing clear intent
- Reduced dependency graphs with better separation of concerns
- Clear package/module boundaries with proper documentation
- Modern Kotlin idioms throughout codebase
- Structured logging instead of println debugging

---

## AI Agent Coordination

**For smaller AI agents working on individual tasks:**

1. **Pick a specific task** from any phase above
2. **Read the task specification** carefully
3. **Follow the provided template** for that task type
4. **Make minimal changes** - only what's needed for that specific task
5. **Verify tests pass** after your changes
6. **Use the suggested commit message** format
7. **Mark the task complete** by changing [ ] to [x]

**Task Priority:**
1. **Phase 1-2:** Mechanical fixes (safe, high-impact)
2. **Phase 3-4:** API improvements (medium risk, high value)
3. **Phase 5-6:** Structural changes (higher risk, architectural value)
4. **Phase 7-8:** Cleanup and modernization (low risk, polish)
5. **Phase 9:** Verification and documentation

**Coordination Rules:**
- Work on different files to avoid conflicts
- Complete entire task before moving to next
- Update this plan with progress
- Ask for clarification if task is unclear


