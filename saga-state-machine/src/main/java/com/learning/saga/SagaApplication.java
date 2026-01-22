package com.learning.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application entry point.
 * 
 * This is a learning project demonstrating:
 * - Saga Pattern for distributed transactions
 * - State Machine for managing saga lifecycle
 * - Chain of Responsibility for hooks
 * - Hexagonal Architecture
 * 
 * @see <a href="docs/README.md">Project Documentation</a>
 */
@SpringBootApplication
public class SagaApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaApplication.class, args);
    }
}
