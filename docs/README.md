![Kotlin](https://img.shields.io/badge/Kotlin-2.2.21-blueviolet)
![JDK](https://img.shields.io/badge/JDK-17-blue)
![Gradle](https://img.shields.io/badge/Gradle-8.14-02303A?logo=gradle)
[![CI](https://github.com/asketmc/guilds-of-bash/actions/workflows/ci.yml/badge.svg)](https://github.com/asketmc/guilds-of-bash/actions/workflows/ci.yml)
![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)

 **Testing strategy & scope:** see [`core-test/TESTING.md`](../core-test/TESTING.md)  
 **Coverage report (latest):** [https://asketmc.github.io/guilds-of-bash/kover/](https://asketmc.github.io/guilds-of-bash/kover/)  
 **Download coverage HTML (latest):** [https://asketmc.github.io/guilds-of-bash/kover-html.zip](https://asketmc.github.io/guilds-of-bash/kover-html.zip)  

# Guilds of Bash

Deterministic guild-management simulation prototype built around a **Command → simulation step → Events** boundary.
The core is implemented as a pure reducer with explicit inputs (state, command, RNG), enabling reproducible execution and testability; adapters handle IO and presentation (currently: a console adapter).

## Core Architecture (invariants)

* Single authoritative state, mutated only via explicit commands
* Reducer-based processing: `Command → step(state, rng) → Events`
* Events are the only observation channel for adapters
* Core is pure: no IO, clocks, platform randomness, or side effects
* Determinism is verified in tests via canonical serialization and state/event hashing

## What it does (PoC)

A minimal, console-driven loop:

* create and post contract drafts (Inbox → Board) with simple terms
* generate hero arrivals; heroes select and take contracts
* advance time in explicit day ticks; contracts progress and resolve into results requiring closure
* sell trophies to a single buyer; track basic economy and region state

## Testing philosophy

Testing is risk-driven.
Core logic is covered by deterministic unit and in-process integration tests.
No jar-level or black-box tests are used at PoC stage, as the current packaging layer does not introduce additional behavior beyond the core.
New test levels are added only when they increase confidence, not by default.

<details>
  <summary><strong>Coverage details</strong> (from CI / Kover)</summary>

* The CI workflow uploads the merged Kover HTML report as an artifact (`kover-html`).
* For PRs, a coverage summary comment is posted automatically.
* For `master`, the latest Kover HTML report is also published to GitHub Pages (links above).

Quick local check:

```
./gradlew test koverHtmlReport
```

</details>

## Quality & CI intent

CI is used as a quality gate and as evidence of correctness, not as a development environment.
The focus is on reproducibility, regression detection, and coverage visibility.
Artifacts are treated as a delivery format, not as a separate test target.

## Status

PoC / feature-freeze at the “M0” scope:

* priority: correctness, reproducibility, and testability; supported by CI and coverage visibility
* gameplay depth/content: intentionally minimal at this stage

## Running

### Local (from source)

```
# Console app
./gradlew :adapter-console:run

# Unit tests
./gradlew test

# Merged HTML coverage report
./gradlew koverHtmlReport
# -> build/reports/kover/html/index.html
```

### Prebuilt CLI jar (latest release)

Download the `adapter-console-cli-*.jar` and its `.sha256` from the latest GitHub Release:

* [https://github.com/asketmc/guilds-of-bash/releases/latest](https://github.com/asketmc/guilds-of-bash/releases/latest)
* Verify checksum (optionally)
* Run:

```bash
java -jar adapter-console-all.jar
```

## Tests (scope)

Tests are oriented toward:

* deterministic behavior under fixed inputs
* state validity across command processing
* persistence roundtrips (serialize/deserialize) where applicable

Non-goals at PoC stage:

* UI/UX testing beyond the console adapter
* performance tuning/benchmarking
* content/balance tuning

## License

GPL-3.0-only
