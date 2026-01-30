# Guilds of Bash - Project Structure

## Directory Organization

### Root Level
```
Guilds-of-Bash/
├── core/                    # Pure simulation logic (no IO/side effects)
├── adapter-console/         # Console REPL interface adapter
├── core-test/              # Comprehensive test suite
├── build-logic/            # Gradle build configuration and plugins
├── config/                 # Static analysis and quality tool configurations
├── ci/                     # CI-specific configurations and baselines
├── scripts/                # Development and CI utility scripts
└── .github/workflows/      # GitHub Actions CI/CD pipeline
```

### Core Module (`core/`)
**Purpose**: Pure deterministic simulation engine
```
core/src/main/kotlin/core/
├── Commands.kt             # All available game commands
├── Events.kt              # All emitted events
├── Reducer.kt             # Main state transition logic
├── state/                 # Game state data structures
├── primitives/            # Basic game enums and value types
├── handlers/              # Command-specific processing logic
├── invariants/            # State validation rules
├── serde/                 # Serialization/deserialization
├── rng/                   # Deterministic random number generation
└── flavour/               # Game content (names, constants)
```

### Console Adapter (`adapter-console/`)
**Purpose**: REPL interface for game interaction
```
adapter-console/src/main/kotlin/console/
├── Main.kt                # Entry point and REPL loop
├── CommandParser.kt       # Parse user input to commands
├── StateRenderer.kt       # Format game state for display
└── EventRenderer.kt       # Format events for display
```

### Test Suite (`core-test/`)
**Purpose**: Comprehensive testing with multiple strategies
```
core-test/src/test/kotlin/test/
├── unit/                  # Unit tests for individual components
├── integration/           # Integration tests for command flows
├── invariants/            # Invariant verification tests
├── serialization/         # Serialization roundtrip tests
├── determinism/           # Reproducibility verification tests
└── golden/                # Golden master tests with saved replays
```

## Core Components & Relationships

### Command-Event Flow
```
User Input → CommandParser → Command → Reducer.step() → Events → StateRenderer
                                    ↓
                              State Update → InvariantVerifier
```

### State Architecture
```
GameState
├── MetaState      # Day, revision, tax info, ID counters
├── GuildState     # Rank, reputation, policies
├── RegionState    # Stability, threat levels
├── EconomyState   # Money, trophies, reserves
├── ContractState  # Inbox, board, active contracts, returns
└── HeroState      # Available heroes, hero data
```

### Key Architectural Patterns

#### Pure Reducer Pattern
- **Input**: `(GameState, Command, RNG) → StepResult(newState, events)`
- **Guarantees**: Deterministic, side-effect free, fully observable
- **Validation**: Pre-command validation with rejection events

#### Event Sourcing
- **Events**: Complete audit trail of all state changes
- **Replay**: Reconstruct any state from event sequence
- **Observability**: Adapters observe state only through events

#### Hexagonal Architecture
- **Core**: Pure business logic with no external dependencies
- **Adapters**: Handle IO, user interface, persistence
- **Ports**: Clean interfaces between core and adapters

## Module Dependencies

### Dependency Graph
```
adapter-console → core
core-test → core
build-logic → (independent)
```

### Key Principles
- **Core Independence**: Core module has no dependencies on adapters
- **Adapter Dependency**: Adapters depend on core, never vice versa
- **Test Isolation**: Tests can run without adapters or external systems
- **Build Logic Separation**: Build configuration isolated in separate module

## Build System Architecture

### Multi-Module Gradle Setup
- **Root Project**: Aggregates coverage, documentation, quality checks
- **Subprojects**: Independent build configurations with shared standards
- **Build Logic**: Centralized plugin and configuration management

### Quality Gates
- **Unit Tests**: Fast feedback on component behavior
- **Integration Tests**: Command flow and state transition verification
- **Mutation Testing**: Test quality assurance with PiTest
- **Static Analysis**: Code quality enforcement with Detekt
- **Coverage**: Merged coverage reporting with Kover

### CI/CD Pipeline
- **Multi-Stage**: Parallel execution of different test types
- **Artifact Generation**: Reproducible JAR builds with checksums
- **Quality Reporting**: Coverage badges and HTML reports
- **Release Automation**: Tagged releases with GitHub artifacts