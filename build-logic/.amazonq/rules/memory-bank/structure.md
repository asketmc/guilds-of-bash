# Guilds of Bash - Project Structure

## Directory Organization

### Root Level Structure
```
Guilds-of-Bash/
├── core/                    # Simulation engine (pure business logic)
├── adapter-console/         # Console UI adapter
├── core-test/              # Comprehensive test suite
├── build-logic/            # Gradle build configuration
├── .github/workflows/      # CI/CD pipeline definitions
├── config/                 # Static analysis and tool configurations
├── scripts/                # Build and development utilities
└── ci/                     # CI-specific configurations
```

## Core Components & Relationships

### 1. Simulation Core (`core/`)
**Purpose**: Pure business logic with no external dependencies
```
core/src/main/kotlin/core/
├── state/                  # Game state management
│   ├── GameState.kt       # Main state container
│   ├── EconomyState.kt    # Economic simulation
│   ├── GuildState.kt      # Guild management
│   └── RegionState.kt     # Regional data
├── serde/                 # Serialization layer
├── primitives/            # Domain value objects
├── invariants/            # State validation rules
├── Commands.kt            # State mutation interface
├── Events.kt              # State observation interface
└── Reducer.kt             # Command processing engine
```

### 2. Console Adapter (`adapter-console/`)
**Purpose**: User interface and IO handling
```
adapter-console/src/main/kotlin/console/
├── Main.kt                # Application entry point
├── Gazette.kt             # Event rendering
├── Flavours.kt            # Text generation
└── UiBox.kt               # Console formatting
```

### 3. Test Suite (`core-test/`)
**Purpose**: Comprehensive validation and quality assurance
```
core-test/src/test/kotlin/
├── test/helpers/          # Test utilities and assertions
├── test/suites/           # Test organization
├── core/partial/          # Integration tests
└── [Various]Test.kt       # Unit and integration tests
```

### 4. Build Logic (`build-logic/`)
**Purpose**: Gradle build configuration and conventions
```
build-logic/src/main/kotlin/
├── gob.kotlin-jvm-base.gradle.kts     # Base Kotlin configuration
├── gob.kotlin-library.gradle.kts     # Library conventions
├── gob.kotlin-application.gradle.kts # Application conventions
└── gob.core-test.gradle.kts          # Test-specific configuration
```

## Architectural Patterns

### Command-Event Architecture
- **Commands**: Explicit state mutations (Commands.kt)
- **Events**: State change notifications (Events.kt)
- **Reducer**: Command processing engine (Reducer.kt)
- **State**: Immutable state containers (state/ package)

### Hexagonal Architecture
- **Core**: Pure business logic (core module)
- **Adapters**: External interfaces (adapter-console module)
- **Ports**: Command/Event interfaces for adapter communication

### Domain-Driven Design
- **Aggregates**: GameState as root aggregate
- **Value Objects**: Primitives package (IDs, Quality, Rank, etc.)
- **Domain Services**: Specialized logic (ContractAttractiveness, HeroPower)
- **Invariants**: Domain rule enforcement (invariants/ package)

## Module Dependencies
```
adapter-console → core
core-test → core
build-logic → (standalone)
```

### Dependency Flow
1. **Core Module**: Self-contained, no external dependencies
2. **Adapter Console**: Depends on core for business logic
3. **Core Test**: Depends on core for testing
4. **Build Logic**: Independent build configuration

## Key Design Principles

### Separation of Concerns
- **Pure Core**: No IO, clocks, or platform dependencies
- **Adapter Layer**: Handles all external interactions
- **Test Layer**: Isolated validation and quality assurance

### Reproducibility
- **Deterministic**: Fixed inputs produce identical outputs
- **Canonical Serialization**: Stable state representation
- **Hash Verification**: State integrity validation

### Extensibility
- **Adapter Pattern**: Easy to add new UI/IO adapters
- **Event System**: Flexible observation mechanism
- **Command Interface**: Standardized state mutation