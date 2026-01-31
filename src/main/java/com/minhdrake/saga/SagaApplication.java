package com.minhdrake.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Saga State Machine Learning Application.
 * 
 * WHY SPRING BOOT:
 * ================
 * Spring Boot provides:
 * 1. Dependency injection for StateHandlers
 * 2. Auto-configuration for common components
 * 3. Easy testing with @SpringBootTest
 * 4. Production-ready features (metrics, health checks)
 * 
 * APPLICATION STRUCTURE:
 * ======================
 * 
 * This application demonstrates:
 * 
 * 1. STATE MACHINE PATTERN
 * - Defined states (OrderStatus enum)
 * - State transitions controlled by handlers
 * - Single source of truth (SagaContext)
 * 
 * 2. SAGA PATTERN (Orchestration)
 * - Coordinator manages step execution
 * - Automatic compensation on failure
 * - Recovery support for system failures
 * 
 * PACKAGE STRUCTURE:
 * ==================
 * com.minhdrake.saga
 * ├── application/ # Application services
 * │ └── handler/ # State handlers
 * │ └── state/ # State machine handlers
 * ├── domain/ # Domain models
 * │ ├── exception/ # Custom exceptions
 * │ └── model/
 * │ ├── saga/ # Saga-related models
 * │ └── type/ # Enums and value objects
 * └── infrastructure/ # (future) Persistence, external services
 */
@SpringBootApplication
public class SagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaApplication.class, args);
    }
}
