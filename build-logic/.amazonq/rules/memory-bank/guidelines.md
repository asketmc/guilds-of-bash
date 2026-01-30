# Guilds of Bash - Development Guidelines

## Code Quality Standards

### Documentation Patterns
- **Comprehensive KDoc**: Every public function includes detailed contract documentation with sections for:
  - `## Contract`: What the function guarantees
  - `## Preconditions`: Required input constraints
  - `## Postconditions`: Guaranteed output state
  - `## Determinism`: Reproducibility guarantees
  - `## Complexity`: Time/space complexity analysis
- **Why Comments**: Explain architectural decisions and design rationale, not just what code does
- **Stability Annotations**: Mark APIs with audience and stability guarantees (e.g., "Stable API: yes; Audience: core reducer only")

### Naming Conventions
- **Descriptive Function Names**: Use full descriptive names like `handleAdvanceDay`, `verifyInvariants`, `serializeEvents`
- **Domain-Specific Terminology**: Consistent use of game domain terms (inbox, board, active, returns, heroes, trophies)
- **Enum Naming**: Use SCREAMING_SNAKE_CASE for enum values (e.g., `ON_MISSION`, `RETURN_READY`, `LOCKED`)
- **File Organization**: Group related functionality in packages (handlers, primitives, state, serde, invariants)

### Code Structure Standards
- **Sealed Classes**: Use for command and event hierarchies to ensure exhaustive when expressions
- **Data Classes**: Prefer for state containers and value objects with automatic equals/hashCode
- **Internal Visibility**: Mark core implementation details as `internal` to prevent external coupling
- **Immutable State**: Use `copy()` methods for state updates, never mutate existing objects

## Architectural Patterns

### Command-Event Architecture
```kotlin
// Standard command handler pattern
internal fun handleCommand(
    state: GameState,
    cmd: Command,
    rng: Rng,
    ctx: SeqContext
): GameState {
    // 1. Validate preconditions
    // 2. Apply state changes
    // 3. Emit events via ctx.emit()
    // 4. Return new state
}
```

### Deterministic Processing
- **Explicit RNG**: Always pass `Rng` instance, never use `Random()` or system randomness
- **Fixed Ordering**: Sort collections by stable keys (IDs) before processing to ensure deterministic iteration
- **RNG Draw Contracts**: Document exact order and count of RNG draws for replay compatibility
- **No Side Effects**: Core logic must be pure - no IO, wall-clock time, or global state access

### Event Sourcing Patterns
```kotlin
// Event emission pattern
ctx.emit(
    EventType(
        day = currentDay,
        revision = state.meta.revision,
        cmdId = cmd.cmdId,
        seq = 0L, // Auto-assigned by context
        // ... event-specific fields
    )
)
```

### State Management
- **Immutable Updates**: Always use `state.copy()` with nested updates
- **Validation After Changes**: Call `verifyInvariants()` after state mutations
- **ID Management**: Increment ID counters monotonically, never reuse IDs
- **Referential Integrity**: Maintain consistent references between state entities

## Implementation Patterns

### Error Handling
- **Command Validation**: Use `canApply()` pattern for pre-validation with specific rejection reasons
- **Invariant Violations**: Emit `InvariantViolated` events rather than throwing exceptions
- **Graceful Degradation**: Handle missing references gracefully with detailed error messages

### Serialization Standards
```kotlin
// Canonical JSON serialization pattern
private fun serializeEvent(event: Event, sb: StringBuilder) {
    sb.appendCommonFields("EventType", event)
    sb.appendIntField("fieldName", event.fieldValue)
    sb.appendStringField("textField", event.textValue)
    sb.append('}')
}
```

### Testing Patterns
- **Factory Methods**: Use reflection-based factories for comprehensive test data generation
- **Variant Testing**: Test multiple scenarios (nulls, empty arrays, edge cases) systematically
- **Determinism Verification**: Verify same inputs produce identical outputs and hashes
- **Exhaustive Coverage**: Ensure all sealed class branches are tested

## API Design Principles

### Internal APIs
```kotlin
// Standard module organization
package core.handlers // Command processing logic
package core.primitives // Basic enums and value types  
package core.state // Game state data structures
package core.serde // Serialization utilities
package core.invariants // State validation rules
```

### Public Interfaces
- **Minimal Surface Area**: Expose only essential functions through public APIs
- **Stable Contracts**: Document breaking change policies and version compatibility
- **Type Safety**: Use value classes for IDs to prevent mixing different ID types
- **Builder Patterns**: Provide convenient construction methods for complex objects

## Performance Considerations

### Efficient Collections
- **Pre-sized Collections**: Use `buildList {}` and `mutableListOf()` with known capacity
- **Lookup Optimization**: Build `associateBy {}` maps for O(1) lookups in hot paths
- **Lazy Sequences**: Use `asSequence()` for multi-step transformations on large collections
- **Sorting Strategy**: Sort by numeric projections (`num()` function) for stable ordering

### Memory Management
- **Immutable Sharing**: Leverage Kotlin's structural sharing for unchanged nested objects
- **Temporary Collections**: Minimize intermediate collection creation in loops
- **String Building**: Use `StringBuilder` for complex string construction (serialization)

## Quality Assurance

### Static Analysis Integration
- **Detekt Configuration**: Use project-specific rules in `config/detekt/detekt.yml`
- **Baseline Management**: Track known issues in `detekt-baseline.xml`
- **Adoption Mode**: Start with warnings, gradually increase strictness

### Test Quality Standards
- **Mutation Testing**: Use PiTest to verify test effectiveness
- **Coverage Targets**: Maintain high coverage with meaningful assertions
- **Golden Master Tests**: Use saved replays for regression detection
- **Property-Based Testing**: Test invariants across wide input ranges

### CI/CD Integration
- **Multi-Stage Pipeline**: Separate fast unit tests from slower integration tests
- **Reproducible Builds**: Ensure consistent artifacts across environments
- **Quality Gates**: Block merges on test failures or quality regressions
- **Automated Reporting**: Generate coverage and quality reports automatically

## Development Workflow

### Code Organization
- **Single Responsibility**: Each class/function has one clear purpose
- **Dependency Direction**: Core never depends on adapters, only vice versa
- **Module Boundaries**: Respect package visibility and avoid circular dependencies
- **Feature Completeness**: Implement features end-to-end before moving to next feature

### Debugging Support
- **Deterministic Hashing**: Use state/event hashes for regression detection
- **Structured Logging**: Print key-value pairs for greppable debug output
- **Replay Capability**: Save command sequences for bug reproduction
- **Observable State**: Make all state changes visible through events