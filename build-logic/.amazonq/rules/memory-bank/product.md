# Guilds of Bash - Product Overview

## Project Purpose
Guilds of Bash is a deterministic guild-management simulation prototype built around a **Command → simulation step → Events** architecture. The core implements a pure reducer with explicit inputs (state, command, RNG), enabling reproducible execution and comprehensive testability.

## Value Proposition
- **Deterministic Simulation**: Every game state change is reproducible given the same inputs and RNG seed
- **Event-Driven Architecture**: All state mutations are observable through events, enabling replay and analysis
- **Pure Core Logic**: No IO, clocks, platform randomness, or side effects in the core simulation
- **Comprehensive Testing**: Risk-driven testing strategy with high coverage and mutation testing

## Key Features & Capabilities

### Core Simulation
- **Guild Management**: Players act as guild branch managers, not heroes
- **Contract System**: Create, post, and manage contracts with customizable terms (fees, salvage policies)
- **Autonomous Heroes**: Heroes make independent decisions about contract selection and execution
- **Economic Pressure**: Tax cycles and guild closure risks create meaningful resource management
- **Regional Stability**: World state affects contract difficulty and threat levels

### Technical Features
- **Command-Event Architecture**: Single authoritative state mutated only via explicit commands
- **Deterministic Execution**: Reproducible simulation with explicit RNG management
- **Event Sourcing**: Complete audit trail of all state changes through events
- **Invariant Verification**: Automatic state validation after each command
- **Serialization Stability**: Canonical JSON serialization for state persistence

### Development Features
- **Multi-Module Architecture**: Clean separation between core logic and adapters
- **Console Adapter**: REPL-based interface for direct game interaction
- **Comprehensive CI/CD**: Automated testing, coverage reporting, and quality gates
- **Mutation Testing**: PiTest integration for testing quality assurance
- **Static Analysis**: Detekt integration for code quality enforcement

## Target Users & Use Cases

### Primary Users
- **Game Developers**: Learning event-driven architecture and deterministic simulation design
- **Software Engineers**: Studying pure functional programming patterns in Kotlin
- **QA Engineers**: Understanding comprehensive testing strategies and mutation testing

### Use Cases
- **Educational**: Teaching deterministic system design and event sourcing patterns
- **Prototyping**: Base for building more complex simulation games
- **Testing Research**: Exploring advanced testing methodologies and coverage strategies
- **Architecture Study**: Understanding clean architecture with pure core and adapter patterns

## Current Status
PoC / feature-freeze at "M0" scope with focus on:
- **Priority**: Correctness, reproducibility, and testability
- **Quality**: Supported by CI and coverage visibility  
- **Gameplay**: Intentionally minimal at this stage for architectural focus

## Player Fantasy (Current Implementation)
Players manage a guild branch as a "survival machine":
- Create and publish contracts with customizable terms
- Observe autonomous hero behavior and decision-making
- Handle contract resolutions and trophy management
- Withstand economic pressure through tax cycles
- Grow guild rank to increase contract volume and hero influx
- Navigate regional stability and scaling threat levels