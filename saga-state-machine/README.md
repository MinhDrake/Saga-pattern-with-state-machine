# Saga + State Machine Learning Project

A learning project demonstrating **Saga Pattern** and **State Machine** implementation for distributed transactions, using an **E-commerce Order Flow** as the domain example.

## 🎯 Learning Objectives

1. **State Machine Pattern**: How to manage transaction states with clear transitions
2. **Saga Pattern**: How to coordinate distributed transactions with compensation logic
3. **Chain of Responsibility**: How to build extensible hook systems
4. **Hexagonal Architecture**: Clean separation between domain, application, and infrastructure

## 📁 Project Structure

```
saga-state-machine/
├── docs/
│   ├── STATE_MACHINE.md           # State machine explanation
│   ├── SAGA_PATTERN.md            # Saga pattern explanation
│   ├── PATTERN_COMPARISON.md      # Orchestration vs Choreography
│   └── diagrams/
│       ├── state-machine.puml
│       └── saga-flow.puml
├── src/main/java/com/learning/saga/
│   ├── domain/                     # Domain Layer (Core Business Logic)
│   │   ├── model/
│   │   │   ├── order/              # Order aggregate
│   │   │   ├── saga/               # Saga-related models
│   │   │   └── type/               # Enums (OrderStatus, ExchangeStatus)
│   │   ├── exception/              # Domain exceptions
│   │   └── repository/             # Repository interfaces (ports)
│   │
│   ├── application/                # Application Layer (Use Cases)
│   │   ├── handler/
│   │   │   ├── state/              # State handlers
│   │   │   └── hook/               # Hook handlers
│   │   ├── saga/                   # Saga engine & coordinator
│   │   ├── service/                # Application services
│   │   └── port/                   # Input/Output ports
│   │
│   └── infrastructure/             # Infrastructure Layer (Adapters)
│       ├── persistence/            # Database implementations
│       ├── messaging/              # Kafka/Message queue
│       ├── api/                    # External API clients
│       └── config/                 # Spring configurations
│
└── src/test/java/                  # Tests
```

## 🔄 Order Flow Example

```
[Customer Places Order]
        │
        ▼
    ┌───────────┐
    │   INIT    │ ← Order created
    └─────┬─────┘
          │
          ▼
    ┌───────────────┐
    │  PROCESSING   │ ← Execute saga steps
    └───────┬───────┘
            │
    ┌───────┴───────┐
    │               │
    ▼               ▼
┌───────┐     ┌───────────┐
│SUCCESS│     │ REVERTING │ ← If any step fails
└───────┘     └─────┬─────┘
                    │
              ┌─────┴─────┐
              │           │
              ▼           ▼
        ┌─────────┐  ┌─────────────┐
        │REVERTED │  │REVERT_FAILED│
        └─────────┘  └─────────────┘
```

## 🛠 Technologies

- Java 17
- Spring Boot 3.x
- Lombok
- (Optional) Redis for timeout management
- (Optional) Kafka for async messaging

## 📚 Key Concepts

### Why State Machine?
- **Predictable transitions**: Only valid state changes are allowed
- **Audit trail**: Every state change is trackable
- **Recovery**: System can resume from any state after failure

### Why Saga Pattern?
- **Distributed atomicity**: All-or-nothing across multiple services
- **Compensation**: Automatic rollback when steps fail
- **Resilience**: Handle partial failures gracefully

## 🚀 Getting Started

```bash
cd saga-state-machine
mvn clean compile
mvn test
```

## 📖 Documentation

- [State Machine Deep Dive](docs/STATE_MACHINE.md)
- [Saga Pattern Explained](docs/SAGA_PATTERN.md)
- [Orchestration vs Choreography](docs/PATTERN_COMPARISON.md)
