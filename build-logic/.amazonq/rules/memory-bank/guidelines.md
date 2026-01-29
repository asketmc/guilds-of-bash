# Guilds of Bash - Development Guidelines

## Code Quality Standards

### Documentation Patterns
- **Comprehensive KDoc**: All public functions include detailed documentation with Contract, Preconditions, Postconditions, Invariants, Determinism, and Complexity sections
- **Why Comments**: Extensive use of "Why" comments explaining design decisions and architectural choices
- **Inline Documentation**: Complex logic includes explanatory comments for maintainability
- **File Headers**: Each file includes purpose and context documentation

### Naming Conventions
- **Descriptive Names**: Functions and variables use clear, descriptive names (e.g., `applyAndPrintWithAnalytics`, `printCmdInput`)
- **Domain Language**: Consistent use of domain terminology (Contract, Hero, Guild, etc.)
- **Enum Naming**: Enums use UPPER_CASE for values (e.g., `BoardStatus.OPEN`, `Outcome.SUCCESS`)
- **Boolean Prefixes**: Boolean properties use clear prefixes (`requiresPlayerClose`, `isPartialPayment`)

### Code Organization
- **Logical Grouping**: Code organized into logical sections with separator comments (`// ─────────────────────────────────────────────────────────────────────────────`)
- **Single Responsibility**: Functions focused on single, well-defined responsibilities
- **Pure Functions**: Core logic implemented as pure functions for determinism
- **Immutable Data**: Extensive use of immutable data structures and `copy()` operations

## Architectural Patterns

### Command-Event Architecture
- **Sealed Interfaces**: Events and Commands defined as sealed interfaces for type safety
- **Event Sourcing**: All state changes communicated through events
- **Command Validation**: Explicit validation before command execution
- **Deterministic Processing**: Commands processed deterministically with explicit RNG

### Error Handling
- **Validation Results**: Use of sealed classes for validation results (`ValidationResult.Rejected`)
- **Early Returns**: Guard clauses and early returns for invalid states
- **Graceful Degradation**: Fallback behaviors for edge cases
- **Debug Flags**: Conditional debug output controlled by constants

### State Management
- **Immutable State**: All state objects are immutable data classes
- **Copy-Based Updates**: State mutations through `copy()` operations
- **Encapsulation**: State access controlled through well-defined interfaces
- **Snapshot Pattern**: State snapshots for analytics and persistence

## Serialization Standards

### JSON Serialization
- **Canonical Format**: Deterministic JSON output with stable field ordering
- **Type Discriminators**: Events include type discriminators as first field
- **Field Ordering**: Consistent field ordering across all serializers
- **Escape Handling**: Proper JSON string escaping for special characters

### Data Transfer Objects
- **DTO Pattern**: Separate DTOs for serialization to isolate domain models
- **Version Control**: Save version tracking for backward compatibility
- **Enum Serialization**: Enums serialized as strings using `.name` property
- **Value Class Handling**: Value classes serialized as raw primitive values

## Testing Patterns

### Test Organization
- **Helper Functions**: Extensive use of test helper functions for common operations
- **Golden File Testing**: Regression testing using golden file comparisons
- **Deterministic Testing**: Fixed seeds for reproducible test results
- **Assertion Helpers**: Custom assertion functions for domain-specific validations

### Test Data Management
- **Factory Pattern**: Test state factories for consistent test data creation
- **Scripted RNG**: Controlled randomness through scripted RNG for predictable tests
- **Event Assertions**: Specialized assertions for event validation
- **Invariant Testing**: Automated invariant checking in tests

## Performance Considerations

### Optimization Patterns
- **Pre-built Lookups**: Use of HashMap and associateBy for O(1) lookups
- **Batch Operations**: Processing multiple items in single operations
- **Lazy Evaluation**: Deferred computation where appropriate
- **Memory Efficiency**: Reuse of collections and StringBuilder for string building

### Collection Usage
- **Immutable Collections**: Preference for immutable collections in public APIs
- **Mutable Builders**: Use of mutable collections for internal building operations
- **Array Usage**: IntArray for primitive collections to avoid boxing
- **Sorted Collections**: Consistent sorting for deterministic output

## Console Adapter Patterns

### User Interface Design
- **Command Parsing**: Robust command parsing with error handling
- **Contextual Output**: Rich context information for debugging
- **Hash Verification**: State and event hashing for regression detection
- **Graceful Error Handling**: User-friendly error messages without crashes

### Output Formatting
- **Structured Logging**: Consistent log format with greppable patterns
- **Progress Indicators**: Clear progress messages for long operations
- **Tabular Data**: Formatted output for lists and status information
- **Flavor Text**: Optional narrative elements for enhanced user experience

## Build and Configuration

### Gradle Conventions
- **Convention Plugins**: Centralized build logic in convention plugins
- **Multi-module Structure**: Clear separation between core, adapters, and tests
- **Quality Gates**: Integrated static analysis, testing, and coverage tools
- **Reproducible Builds**: Deterministic build artifacts with stable timestamps

### Code Quality Tools
- **Detekt Integration**: Static analysis with baseline management
- **Kover Coverage**: Comprehensive test coverage reporting
- **Mutation Testing**: PiTest integration for test quality validation
- **Documentation Generation**: Automated API documentation with Dokka

## Development Workflow

### Version Control
- **Atomic Commits**: Small, focused commits with clear messages
- **Branch Strategy**: Feature branches with clean merge history
- **CI Integration**: Automated testing and quality checks on all changes
- **Release Automation**: Automated release process with version management

### Code Review Standards
- **Comprehensive Reviews**: Focus on architecture, performance, and maintainability
- **Documentation Review**: Ensure all public APIs are properly documented
- **Test Coverage**: Verify adequate test coverage for new functionality
- **Performance Impact**: Consider performance implications of changes