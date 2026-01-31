# Saga State Machine Project

A Spring Boot application demonstrating the implementation of **Saga Pattern** and **State Machine Pattern** for robust distributed transaction management and order processing workflows.

## 🚀 Overview

This project serves as a reference implementation for handling complex business transactions and state transitions in a microservices-like architecture. It showcases how to maintain data consistency and manage state transitions effectively using industry-standard patterns.

### Key Concepts

*   **State Machine Pattern**: Manages entity states (e.g., `OrderStatus`) with defined transitions, ensuring that entities always move through a valid sequence of states.
*   **Saga Pattern (Orchestration)**: Coordinates long-running transactions across multiple steps. If a step fails, the Saga coordinator executes compensating transactions to roll back the changes, ensuring eventual consistency.

## 🛠️ Technology Stack

*   **Java**: 17
*   **Framework**: Spring Boot 3.0.4
*   **Build Tool**: Maven
*   **Utilities**: Lombok

## 📂 Project Structure

The project follows a Domain-Driven Design (DDD) inspired structure:

```
com.minhdrake.saga
├── application/       # Application layer services
│   ├── handler/       # Handlers for specific state transitions
│   └── state/         # core State machine logic
├── domain/            # Domain layer (business logic)
│   ├── model/         # Entities, Value Objects, and Enums
│   │   ├── saga/      # Saga-specific models
│   │   └── type/      # Domain types (e.g., OrderStatus)
│   └── exception/     # Custom domain exceptions
└── infrastructure/    # Infrastructure layer (persistence, etc.)
```

## 🚀 Getting Started

### Prerequisites

*   JDK 17 or higher
*   Maven 3.6+

### Build the Project

```bash
mvn clean install
```

### Run the Application

```bash
mvn spring-boot:run
```

## 🧪 Testing

Run the test suite to verify the state machine and saga logic:

```bash
mvn test
```

## 📝 Features

*   **Centralized State Management**: `SagaContext` acts as the single source of truth for the transaction state.
*   **Pluggable Handlers**: Easy to add new state handlers for different steps of the business process.
*   **Automatic Compensation**: Built-in mechanism to trigger compensating actions when a step fails.

## 📄 License

This project is for educational purposes.
