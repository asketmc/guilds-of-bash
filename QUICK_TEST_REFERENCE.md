# Quick Test Reference Card

## 🚀 Common Commands

```powershell
# Run the core-test module (all tests)
.\gradlew.bat :core-test:test

# Run a specific test class (use the fully-qualified test class name or pattern)
.\gradlew.bat :core-test:test --tests "test.P1_009_ReducerCriticalTest"

# Run a specific test method (use Gradle test filter patterns)
.\gradlew.bat :core-test:test --tests "test.P1_009_ReducerCriticalTest.someMethodPattern*"

# Run with more Gradle logging
.\gradlew.bat :core-test:test --info

# Force rerun tests (ignore up-to-date)
.\gradlew.bat :core-test:test --rerun-tasks

# Build a module (this may also run tests depending on task ordering)
.\gradlew.bat :core:build
.\gradlew.bat :adapter-console:build

# Clean and rebuild everything
.\gradlew.bat clean build
```

> Note: Linux/macOS users can drop the `.bat` suffix and use `./gradlew`.

## 📊 Test Suite Overview

- Tests for the project's core logic live in `core-test/src/test/kotlin/test/`.
- Files are organized with a human-readable prefix convention (for example `P1_###_Name.kt`) indicating priority/area and an identifying name — treat the file names as stable anchors for developers, not rigid indices.
- The tests use Kotlin + JUnit5 (with `kotlin.test` helpers and some JUnit annotations visible in the code). A lightweight `@Smoke` tag/annotation is provided for ultra-fast PR smoke runs.
- Avoid treating a single number (total test count) as canonical in docs — tests change frequently. Instead, the reference describes where tests live, how they are tagged, and how CI consumes them.

## 🔎 What's in the `core-test` module

- Location: `core-test/src/test/kotlin/test/`
- Patterns you will see:
  - File naming: `P1_001_...`, `P1_002_...`, etc. (P1 indicates critical tests in this repo's convention)
  - Shared helpers & harnesses are in `TestHelpers.kt` (scenario runner helpers like `runScenario`, `Scenario`, `ScenarioResult`) and utility assertions (e.g. `assertNoRejections`, `assertNoInvariantViolations`, `assertEventTypesPresent`).
  - A `Smoke` annotation is available (`Smoke.kt`) to mark the smallest, fastest subset of tests.
- Test flavor: unit-level deterministic scenarios driven by a pure `step(state, command, rng)` reducer with an explicit `Rng` seed for reproducibility.

## 📁 Test Reports

When you run Gradle tests, results and reports are produced under the `core-test/build/` directory:

- Machine-readable XML (per-test-suite): `core-test/build/test-results/test/*.xml` — useful for CI and custom parsing.
- Human-friendly HTML summary: `core-test/build/reports/tests/test/index.html` — open it in a browser for a readable report.

If those files are not present locally, run the test task above to generate them.

## 🐞 Debugging Failed Tests

- To inspect the HTML report, open the generated file in your OS's default browser:

```powershell
Start-Process core-test\build\reports\tests\test\index.html
```

- To view XML results on Windows, use `Get-Content` or open in an editor:

```powershell
Get-Content core-test\build\test-results\test\*.xml
```

- Quick grep-like status (PowerShell example):

```powershell
Get-ChildItem -Path core-test\build\test-results\test -Filter *.xml | Get-Content | Select-String -Pattern 'testsuite' -Context 0,0
```

- For detailed Gradle-run output, add `--info` or `--stacktrace` flags to the Gradle command to surface failure details.

## 🧭 Troubleshooting & Tips

- If a Gradle `test` run produces no results, ensure you ran the `:core-test:test` task from the repository root and that the build completed.
- Use the `@Smoke` tag on classes you want as a tiny PR smoke suite.
- For exact test selection use Gradle `--tests` with fully-qualified class names (package + class name) or patterns — this is preferred over editing tests for quick iteration.

## 📚 Related Documentation

- Full test implementation notes: `P1_TEST_IMPLEMENTATION_SUMMARY.md`
- Module-specific test details and design notes: `core-test/TEST_SUMMARY.md`
- PoC manifest and command/event specs: `POC_MANIFEST.md`

---

**Last reviewed:** 2026-01-21
**Status:** This document describes the testing *structure and intent* rather than a fixed numeric snapshot; consult `core-test/src/test/kotlin/test/` for the live layout.
