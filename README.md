![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blueviolet)
![JDK](https://img.shields.io/badge/JDK-17-blue)
![Gradle](https://img.shields.io/badge/Gradle-8.14-02303A?logo=gradle)
[![CI](https://github.com/asketmc/guilds-of-bash/actions/workflows/ci.yml/badge.svg)](https://github.com/asketmc/guilds-of-bash/actions/workflows/ci.yml)
![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)

# Guilds of Bash

Deterministic guild-management simulation prototype with a `Command → step(state, rng) → Events` boundary.

## Evidence
- Testing: [`core-test/TESTING.md`](core-test/TESTING.md)
- Coverage: https://asketmc.github.io/guilds-of-bash/kover/
- CI: [`.github/workflows/`](.github/workflows/)
- Docs: [`docs/`](docs/)


## Core model
- Single authoritative state; changes only via commands.
- Pure core (no IO, clocks, platform randomness, side effects).
- Explicit RNG input; draw count is tracked.
- Events are the only observation channel for adapters.
- Invariants are checked after each step; violations are emitted as events.
- Determinism is **test-enforced** via canonical serialization + hashing (golden replays).

**Modules**
- [`core/`](core/) — state, commands/events, reducer, invariants, serialization, RNG
- [`core-test/`](core-test/) — deterministic unit/integration, golden replays, invariant checks
- [`adapter-console/`](adapter-console/) — console adapter (IO/parsing/rendering)

## Run
```bash
./gradlew :adapter-console:run
./gradlew test
./gradlew koverHtmlReport
# -> build/reports/kover/html/index.html
````

<details>
  <summary><strong>Implemented features</strong></summary>

* Console loop: create/post contracts, day ticks, resolve outcomes, close returns.
* Autonomous hero pickup/refusal and contract execution.
* Economy: trophies, fee escrow, sales; rank progression; stability/threat scaling; weekly tax cycle.
* Fraud pipeline (warn/ban + rumor/reputation scheduling) as deterministic segment.

</details>

<details>
  <summary><strong>Prebuilt jar</strong></summary>

Latest release artifacts: [https://github.com/asketmc/guilds-of-bash/releases/latest](https://github.com/asketmc/guilds-of-bash/releases/latest)

```bash
java -jar adapter-console-all.jar
```

</details>

<details>
  <summary><strong>Quality automation</strong></summary>

Workflows: [`.github/workflows/`](.github/workflows/)

* unit tests: [`ci2_unit_tests.yml`](.github/workflows/ci2_unit_tests.yml)
* mutation baseline: [`ci3_pitest.yml`](.github/workflows/ci3_pitest.yml)
* fast flaky detection: [`ci4_fast_flaky.yml`](.github/workflows/ci4_fast_flaky.yml)
* full/nightly + quarantine: [`ci8_nightly_full_quarantine.yml`](.github/workflows/ci8_nightly_full_quarantine.yml)
* static analysis: [`ci6_detekt.yml`](.github/workflows/ci6_detekt.yml), [`config/detekt/`](config/detekt/)
* build artifact / release: [`ci7_build_artifact.yml`](.github/workflows/ci7_build_artifact.yml), [`ci9_release.yml`](.github/workflows/ci9_release.yml)
* merged coverage + publication: [`ci5_full_tests_coverage_badge.yml`](.github/workflows/ci5_full_tests_coverage_badge.yml), [`ci1_docs.yml`](.github/workflows/ci1_docs.yml)

</details>

## License

GPL-3.0-only
