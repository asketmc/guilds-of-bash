# Guilds of Bash - Technology Stack

## Programming Languages & Versions

### Primary Language
- **Kotlin**: 2.2.21 (JVM target)
- **JVM Target**: Java 17
- **Java Toolchain**: OpenJDK 17

### Language Features Used
- **Sealed Classes**: Command and Event hierarchies
- **Data Classes**: State containers and value objects
- **Object Declarations**: Singletons for utilities and constants
- **Extension Functions**: Enhanced API ergonomics
- **Kotlinx Serialization**: JSON serialization with stability guarantees

## Build System & Dependencies

### Build Tools
- **Gradle**: 8.14 with Kotlin DSL
- **Gradle Wrapper**: Ensures consistent build environment
- **Multi-Module**: Clean separation of concerns

### Core Dependencies
```kotlin
// Serialization
implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")

// Testing (core-test module)
testImplementation("org.junit.jupiter:junit-jupiter:5.11.4")
testImplementation("org.assertj:assertj-core:3.26.3")
```

### Build Plugins
- **Kotlin JVM**: `kotlin("jvm")` version 2.2.21
- **Kotlin Serialization**: `kotlin("plugin.serialization")` version 2.2.21
- **Kover**: Coverage reporting version 0.9.3
- **Detekt**: Static analysis version 1.23.8
- **Shadow**: Fat JAR creation version 9.2.2
- **PiTest**: Mutation testing version 1.19.0-rc.3
- **Dokka**: Documentation generation version 2.2.0-Beta

## Development Commands

### Basic Operations
```bash
# Run console application
./gradlew :adapter-console:run

# Run all tests
./gradlew test

# Generate coverage report
./gradlew koverHtmlReport
# Output: build/reports/kover/html/index.html

# Run static analysis
./gradlew detekt

# Build fat JAR
./gradlew :adapter-console:shadowJar
```

### Quality Assurance
```bash
# Run mutation tests
./gradlew pitest

# Generate documentation
./gradlew dokkaHtmlMultiModule

# Full quality check
./gradlew check

# Clean build
./gradlew clean build
```

### CI/CD Commands
```bash
# Install CI tools (Windows)
scripts/install-ci-tools.ps1

# Run core tests only
scripts/run-core-test.ps1

# Generate build info
scripts/write-build-info.sh
```

## Testing Framework

### Test Libraries
- **JUnit 5**: Primary testing framework
- **AssertJ**: Fluent assertions library
- **Kover**: Coverage measurement and reporting
- **PiTest**: Mutation testing for test quality

### Test Categories
- **Unit Tests**: Fast, isolated component tests
- **Integration Tests**: Command flow and state transition tests
- **Determinism Tests**: Reproducibility verification
- **Serialization Tests**: Roundtrip stability verification
- **Invariant Tests**: State validation rule verification
- **Golden Tests**: Regression prevention with saved replays

## Quality Tools Configuration

### Static Analysis (Detekt)
- **Configuration**: `config/detekt/detekt.yml`
- **Baseline**: `config/detekt/detekt-baseline.xml`
- **Target**: JVM 17
- **Mode**: Adoption mode (warnings, not failures)

### Coverage (Kover)
- **Merged Reporting**: Aggregates core and core-test modules
- **HTML Output**: `build/reports/kover/html/index.html`
- **CI Integration**: Automatic coverage badge generation

### Mutation Testing (PiTest)
- **Baseline**: `ci/pitest-baseline.json`
- **Target**: Core module only
- **Integration**: Gradle plugin with custom configuration

## Serialization & Persistence

### JSON Serialization
- **Library**: Kotlinx Serialization JSON
- **Strategy**: Canonical format for deterministic hashing
- **Stability**: Documented serialization contracts in `SERIALIZATION_STABILITY.md`

### State Persistence
- **Format**: JSON with explicit field ordering
- **Hashing**: SHA-256 for state and event verification
- **Reproducibility**: Deterministic serialization for replay systems

## Development Environment

### IDE Support
- **IntelliJ IDEA**: Primary development environment
- **Kotlin Plugin**: Latest stable version
- **Gradle Integration**: Built-in Gradle support

### Version Control
- **Git**: Primary VCS with GitHub hosting
- **Branching**: Feature branches with PR workflow
- **CI Integration**: GitHub Actions for automated testing

### Platform Support
- **Primary**: Windows (development environment)
- **CI**: Linux (GitHub Actions runners)
- **JVM**: Cross-platform compatibility via Java 17

## Deployment & Distribution

### Artifact Generation
- **Fat JAR**: Self-contained executable with all dependencies
- **Checksums**: SHA-256 verification for release artifacts
- **Reproducible Builds**: Consistent timestamps and file ordering

### Release Process
- **Automated**: GitHub Actions workflow for tagged releases
- **Artifacts**: Console JAR with checksum verification
- **Documentation**: Auto-generated API docs and coverage reports