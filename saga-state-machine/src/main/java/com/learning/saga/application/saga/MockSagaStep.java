package com.learning.saga.application.saga;

import com.learning.saga.domain.model.saga.AbstractSagaStep;
import com.learning.saga.domain.model.saga.StepResult;
import com.learning.saga.domain.model.type.StepAction;
import lombok.extern.slf4j.Slf4j;

import java.util.Random;
import java.util.UUID;

/**
 * MockSagaStep is a test implementation that simulates external service calls.
 * 
 * WHY A MOCK:
 * ===========
 * For the learning project, we don't have real external services.
 * This mock simulates:
 * - Successful operations
 * - Random failures (for testing compensation)
 * - Async operations (PENDING status)
 * 
 * IN PRODUCTION:
 * ==============
 * Replace with real implementations:
 * - PaymentSagaStep: Integrates with payment gateway
 * - InventorySagaStep: Integrates with inventory system
 * - ShippingSagaStep: Integrates with logistics provider
 * 
 * CONFIGURABLE BEHAVIOR:
 * ======================
 * - Default: Always succeeds
 * - Can inject failure rate for testing
 * - Can simulate async operations
 */
@Slf4j
public class MockSagaStep extends AbstractSagaStep {

    private final String serviceType;
    private final String resourceId;
    private final Random random = new Random();
    
    // Configuration for simulating different behaviors
    private double failureRate = 0.0; // 0 = never fail, 1 = always fail
    private double pendingRate = 0.0; // Rate of PENDING results
    private String simulatedExternalRef;

    public MockSagaStep(long orderId, int index, StepAction action, String serviceType, String resourceId) {
        super(orderId, index, action, serviceType);
        this.serviceType = serviceType;
        this.resourceId = resourceId;
        this.simulatedExternalRef = UUID.randomUUID().toString();
    }

    /**
     * Configure this step to fail randomly at the given rate.
     * @param rate Failure probability (0.0 to 1.0)
     */
    public MockSagaStep withFailureRate(double rate) {
        this.failureRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }

    /**
     * Configure this step to return PENDING randomly at the given rate.
     * @param rate Pending probability (0.0 to 1.0)
     */
    public MockSagaStep withPendingRate(double rate) {
        this.pendingRate = Math.max(0.0, Math.min(1.0, rate));
        return this;
    }

    @Override
    protected StepResult doExecute() {
        log.info("[MockStep:{}] Executing {} for resource: {}", getStepId(), getAction(), resourceId);

        // Simulate some processing time
        simulateLatency();

        // Check for random failure
        if (random.nextDouble() < failureRate) {
            log.warn("[MockStep:{}] Simulated failure", getStepId());
            return StepResult.failed(
                com.learning.saga.domain.model.type.ErrorCode.INTERNAL_ERROR,
                "Simulated failure for testing"
            );
        }

        // Check for pending (async) result
        if (random.nextDouble() < pendingRate) {
            log.info("[MockStep:{}] Simulated PENDING (async operation)", getStepId());
            return StepResult.pending(simulatedExternalRef);
        }

        // Success!
        log.info("[MockStep:{}] Simulated SUCCESS", getStepId());
        return StepResult.success(simulatedExternalRef);
    }

    @Override
    protected StepResult doQuery() {
        log.info("[MockStep:{}] Querying status for resource: {}", getStepId(), resourceId);
        
        // For mock, always return success when queried
        // In real implementation, would call external API
        return StepResult.success(simulatedExternalRef);
    }

    private void simulateLatency() {
        try {
            // Simulate 10-50ms latency
            Thread.sleep(10 + random.nextInt(40));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Get the resource ID this step operates on.
     */
    public String getResourceId() {
        return resourceId;
    }

    /**
     * Get the service type this step interacts with.
     */
    public String getServiceType() {
        return serviceType;
    }
}
