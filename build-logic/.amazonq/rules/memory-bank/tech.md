# Guilds of Bash - Technology Stack

## Programming Languages & Versions
- **Kotlin**: 2.2.21 (primary language)
- **JDK**: 17 (target runtime)
- **Gradle**: 8.14 (build system)

## Build System & Dependencies

### Gradle Configuration
- **Multi-module project** with composite build structure
- **Build Logic**: Centralized in `build-logic/` composite build
- **Convention Plugins**: Standardized module configurations
- **Reproducible Builds**: Deterministic archive generation

### Key Gradle Plugins
```kotlin
// Core Kotlin
kotlin("jvm") version "2.2.21"
kotlin("plugin.serialization") version "2.2.21"

// Documentation
id("org.jetbrains.dokka") version "2.2.0-Beta"

// Testing & Quality
id("org.jetbrains.kotlinx.kover") version "0.9.3"  // Coverage
id("info.solidsoft.pitest") version "1.19.0-rc.3" // Mutation testing
id("io.gitlab.arturbosch.detekt") version "1.23.8" // Static analysis

// Packaging
id("com.gradleup.shadow") version "9.2.2"  // Fat JAR creation
```

### Dependencies
- **Kotlin Serialization**: JSON serialization for state persistence
- **Kotlin Standard Library**: Core language features
- **JUnit**: Testing framework (implied by test structure)

## Development Commands

### Running the Application
```bash
# Console application
./gradlew :adapter-console:run

# Build all modules
./gradlew build
```

### Testing & Quality Assurance
```bash
# Run all tests
./gradlew test

# Generate coverage report
./gradlew koverHtmlReport
# Output: build/reports/kover/html/index.html

# Run mutation testing
./gradlew pitest

# Static analysis
./gradlew detekt

# Full quality check
./gradlew check
```

### Documentation
```bash
# Generate API documentation
./gradlew dokkaHtmlMultiModule
# Output: build/reports/dokka/html/
```

## CI/CD Pipeline

### GitHub Actions Workflows
- **ci.yml**: Main CI orchestration
- **ci1_docs.yml**: Documentation generation
- **ci2_unit_tests.yml**: Unit test execution
- **ci3_pitest.yml**: Mutation testing
- **ci4_fast_flaky.yml**: Fast feedback tests
- **ci5_full_tests_coverage_badge.yml**: Full test suite + coverage
- **ci6_detekt.yml**: Static analysis
- **ci7_build_artifact.yml**: Build artifact generation
- **ci8_nightly_full_quarantine.yml**: Nightly comprehensive testing
- **ci9_release.yml**: Release automation

### Quality Gates
- **Test Coverage**: Kover integration with badge generation
- **Mutation Testing**: PiTest for test quality validation
- **Static Analysis**: Detekt for code quality
- **Reproducible Builds**: Deterministic artifact generation

## Development Environment

### Required Tools
- **JDK 17**: Java Development Kit
- **Gradle 8.14**: Build automation (via wrapper)
- **Git**: Version control
- **IDE**: IntelliJ IDEA recommended (Kotlin support)

### Optional Tools
- **Docker**: For containerized builds (if needed)
- **PowerShell**: For Windows-specific scripts

## Module-Specific Technology

### Core Module
- **Pure Kotlin**: No external dependencies
- **Kotlinx Serialization**: State persistence
- **Immutable Data Structures**: State management
- **Sealed Classes**: Type-safe domain modeling

### Adapter Console
- **Kotlin Standard Library**: Console I/O
- **Text Processing**: String manipulation and formatting
- **ANSI Escape Codes**: Console formatting (implied by UiBox)

### Core Test
- **JUnit Platform**: Test execution
- **Kotlin Test**: Assertions and test utilities
- **Custom Test Helpers**: Domain-specific testing utilities
- **Golden File Testing**: Regression testing approach

## Configuration Files

### Build Configuration
- `build.gradle.kts`: Root build configuration
- `settings.gradle.kts`: Project structure definition
- `gradle.properties`: Build properties
- `build-logic/`: Convention plugin definitions

### Quality Tools
- `config/detekt/detekt.yml`: Static analysis rules
- `config/detekt/detekt-baseline.xml`: Analysis baseline
- `ci/pitest-baseline.json`: Mutation testing baseline

### CI Configuration
- `.github/workflows/`: GitHub Actions definitions
- `scripts/`: Build and development utilities

## Platform Support
- **Primary**: JVM (Java 17+)
- **Operating Systems**: Cross-platform (Windows, macOS, Linux)
- **Architecture**: Any JVM-supported architecture