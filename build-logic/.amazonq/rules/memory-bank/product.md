# Guilds of Bash - Product Overview

## Project Purpose
Guilds of Bash is a deterministic guild-management simulation prototype built around a **Command → simulation step → Events** architecture. The project demonstrates a pure, reproducible simulation core with clean separation between business logic and presentation layers.

## Core Value Proposition
- **Deterministic Simulation**: No clocks, platform randomness, or IO inside the core - ensuring reproducible behavior
- **Command-Event Architecture**: Explicit commands as the only state mutation mechanism, with events as the observation channel
- **Clean Architecture**: Clear separation between simulation core and adapters (IO/presentation)
- **Test-Driven Development**: Comprehensive test coverage with focus on correctness and reproducibility

## Key Features & Capabilities

### Simulation Features
- **Contract Management**: Create and post contract drafts from Inbox to Board with customizable terms
- **Hero System**: Generate hero arrivals, hero selection and contract assignment
- **Time Progression**: Explicit day-tick advancement with contract progression and resolution
- **Economy Simulation**: Trophy selling to buyers, basic economy tracking, and region state management
- **State Validation**: Built-in invariant checks and validation as part of command processing

### Technical Features
- **Reproducibility**: Canonical serialization and hashing for deterministic behavior
- **Event Sourcing**: Events as the primary observation mechanism for adapters
- **State Management**: Single authoritative state model with explicit mutation boundaries
- **Serialization**: Stable serialize/deserialize capabilities for persistence

## Target Users
- **Game Developers**: Learning clean architecture patterns for simulation games
- **Software Engineers**: Studying command-event patterns and deterministic system design
- **Students**: Understanding separation of concerns in complex domain modeling
- **Researchers**: Exploring reproducible simulation architectures

## Current Status (PoC/M0)
- **Feature Freeze**: Intentionally minimal gameplay depth at this stage
- **Priority Focus**: Correctness, reproducibility, comprehensive testing, CI/CD, coverage visibility
- **Architecture Maturity**: Core patterns established and validated
- **Adapter Layer**: Currently console-only, designed for extensibility

## Use Cases
- **Educational**: Learning clean architecture and domain-driven design principles
- **Prototyping**: Base for building more complex simulation games
- **Research**: Studying deterministic system behavior and reproducibility
- **Development**: Template for command-event architectures in Kotlin/JVM