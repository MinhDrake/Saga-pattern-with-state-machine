package com.learning.saga.application.saga;

import com.learning.saga.domain.model.saga.SagaContext;

import java.util.Optional;

/**
 * SagaEngine is the main entry point for saga operations.
 * 
 * WHY AN ENGINE:
 * ==============
 * The engine provides a clean API for saga operations:
 * - start(): Begin a new saga
 * - resume(): Continue a paused saga
 * - query(): Get current saga state
 * 
 * It hides the complexity of state handlers and hooks from callers.
 * 
 * RESPONSIBILITIES:
 * =================
 * 1. Create and initialize SagaContext
 * 2. Delegate to appropriate StateHandler
 * 3. Handle callbacks and resumption
 * 4. Provide query operations
 * 
 * NOT RESPONSIBILITIES:
 * =====================
 * - Actual step execution (that's in StateHandlers)
 * - Hook execution (that's in HookHandlers)
 * - Persistence (that's in Repository)
 */
public interface SagaEngine {

    /**
     * Start a new saga.
     * 
     * @param command The start command with order details
     * @return The saga context after processing
     */
    SagaContext start(StartSagaCommand command);

    /**
     * Resume a paused saga (e.g., after callback received).
     * 
     * @param command The resume command with callback data
     * @return The saga context after processing
     */
    SagaContext resume(ResumeSagaCommand command);

    /**
     * Query the current state of a saga.
     * 
     * @param orderId The order ID
     * @return The saga context if found
     */
    Optional<SagaContext> query(long orderId);

    /**
     * Check if a saga exists for the given order.
     * 
     * @param orderNo The external order number
     * @return true if saga exists
     */
    boolean exists(String orderNo);
}
