![Kotlin](https://img.shields.io/badge/Kotlin-1.9-blueviolet)
![JDK](https://img.shields.io/badge/JDK-17-blue)
![Gradle](https://img.shields.io/badge/Gradle-8.14-02303A?logo=gradle)
[![CI](https://github.com/asketmc/guilds-of-bash/actions/workflows/ci.yml/badge.svg)](https://github.com/asketmc/guilds-of-bash/actions/workflows/ci.yml)
![License: GPL-3.0](https://img.shields.io/badge/License-GPLv3-blue.svg)

> 📄 **Testing strategy & scope:** see [`core-test/TESTING.md`](core-test/TESTING.md)  
> 📊 **Coverage report (latest):** https://asketmc.github.io/guilds-of-bash/kover/  
> ⬇️ **Download coverage HTML (latest):** https://asketmc.github.io/guilds-of-bash/kover-html.zip

# Guilds of Bash

<details>
  <summary><strong>Coverage details</strong> (from CI / Kover)</summary>

- The CI workflow uploads the merged Kover HTML report as an artifact (`kover-html`).
- For PRs, a coverage summary comment is posted automatically.
- For `master`, the latest Kover HTML report is also published to GitHub Pages (links above).

Quick local check:

  ```
  ./gradlew test koverHtmlReport
  ```

</details>

Deterministic guild-management simulation prototype built around a **Command → simulation step → Events** boundary. The simulation core is designed to be pure and reproducible; adapters handle IO and presentation (currently: a console adapter).

## What it does (PoC)

A minimal, console-driven loop:
- create and post contract drafts (Inbox → Board) with simple terms
- generate hero arrivals; heroes select and take contracts
- advance time in explicit day ticks; contracts progress and resolve into results requiring closure
- sell trophies to a single buyer; track basic economy and region state

## Architecture

- **Simulation core**
  - single authoritative state model
  - explicit commands as the only way to mutate state
  - events as the only observation channel for adapters
  - no clocks, no platform randomness, no IO inside the core
  - validation/invariant checks as part of processing
  - reproducibility aids (e.g., canonical serialization + hashing) used in tests

- **Adapters**
  - responsible for input/output and rendering
  - currently: console adapter

## Status

PoC / feature-freeze at the “M0” scope:
- priority: correctness, reproducibility, tests, CI, coverage visibility
- gameplay depth/content: intentionally minimal at this stage

## Running

```
# Console app
./gradlew :adapter-console:run

# Unit tests
./gradlew test

# Merged HTML coverage report
./gradlew koverHtmlReport
# -> build/reports/kover/html/index.html
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
