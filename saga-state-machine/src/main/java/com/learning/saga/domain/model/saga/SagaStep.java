package com.learning.saga.domain.model.saga;

import com.learning.saga.domain.model.type.StepAction;
import com.learning.saga.domain.model.type.StepStatus;

/**
 * SagaStep interface defines the contract for each step in a saga.
 * 
 * WHY AN INTERFACE:
 * =================
 * 1. ABSTRACTION: Different step implementations (payment, inventory, shipping)
 *    all share the same contract.
 * 2. TESTABILITY: Easy to mock steps in unit tests.
 * 3. EXTENSIBILITY: Add new step types without changing existing code.
 * 
 * STEP LIFECYCLE:
 * ===============
 * 1. Step is created with UNKNOWN status
 * 2. execute() is called → status changes based on result
 * 3. If saga fails, compensate() is called on SUCCEEDED steps
 * 
 * KEY DESIGN DECISIONS:
 * =====================
 * 1. Steps are IMMUTABLE after creation (except status updates)
 * 2. Each step has a unique stepId for tracking
 * 3. Steps know their position (index) in the saga sequence
 * 4. Steps can query their status from external systems
 */
public interface SagaStep {

    /**
     * Get the unique identifier for this step.
     * 
     * FORMAT: {orderId}:{index}:{action}:{serviceType}
     * EXAMPLE: "12345:001:CHARGE_PAYMENT:PAYMENT"
     * 
     * WHY THIS FORMAT:
     * - orderId: Links step to its parent saga
     * - index: Execution order
     * - action: What the step does
     * - serviceType: Which service handles this step
     */
    String getStepId();

    /**
     * Get the order ID this step belongs to.
     */
    long getOrderId();

    /**
     * Get the position of this step in the saga sequence.
     * Steps are executed in order: 0, 1, 2, ...
     */
    int getIndex();

    /**
     * Get the type of action this step performs.
     */
    StepAction getAction();

    /**
     * Get the current execution status of this step.
     */
    StepStatus getStatus();

    /**
     * Get the reason/error information if step failed.
     */
    StepResult getResult();

    /**
     * Execute this saga step.
     * 
     * WHY RETURN StepResult:
     * - Encapsulates both success and failure information
     * - Allows steps to return partial data even on failure
     * - Provides context for compensation decisions
     * 
     * IMPLEMENTATION NOTES:
     * - Should be IDEMPOTENT (safe to retry)
     * - Should handle timeouts gracefully
     * - Should NOT throw exceptions (return FAILED status instead)
     * 
     * @return StepResult containing the execution outcome
     */
    StepResult execute();

    /**
     * Update the step status based on external callback or query result.
     * 
     * WHY NEEDED:
     * For async operations, we submit a request and later receive
     * the result via callback. This method applies that result.
     * 
     * @param result The result from callback/query
     * @return true if update was applied, false if ignored (e.g., already completed)
     */
    boolean updateStatus(StepResult result);

    /**
     * Query the current status from the external system.
     * 
     * WHY NEEDED:
     * When recovering from failures, we need to know the actual
     * status of operations that may have completed while we were down.
     * 
     * @return StepResult with the queried status
     */
    StepResult query();

    /**
     * Check if this step needs compensation.
     * Only steps that SUCCEEDED need to be compensated.
     */
    default boolean needsCompensation() {
        return getStatus() == StepStatus.SUCCEEDED 
            && getAction().requiresCompensation();
    }

    /**
     * Create a log representation of this step for persistence.
     */
    StepLog toLog();
}
