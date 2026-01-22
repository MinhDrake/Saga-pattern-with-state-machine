package com.learning.saga.domain.model.saga;

import com.learning.saga.domain.model.type.StepAction;
import com.learning.saga.domain.model.type.StepStatus;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Instant;

/**
 * AbstractSagaStep provides base implementation for saga steps.
 * 
 * WHY ABSTRACT CLASS:
 * ===================
 * 1. SHARED LOGIC: Common code for all step types (logging, status tracking)
 * 2. TEMPLATE METHOD: Subclasses implement doExecute(), we handle the rest
 * 3. CONSISTENCY: All steps behave the same way for status updates
 * 
 * SUBCLASS RESPONSIBILITIES:
 * ==========================
 * 1. Implement doExecute() with actual business logic
 * 2. Implement doQuery() to check status from external system
 * 3. Provide step-specific metadata
 * 
 * EXAMPLE SUBCLASSES:
 * ===================
 * - PaymentSagaStep: Charges customer payment
 * - InventorySagaStep: Reserves product inventory
 * - ShippingSagaStep: Creates delivery order
 */
@Slf4j
@Getter
public abstract class AbstractSagaStep implements SagaStep {

    protected final String stepId;
    protected final long orderId;
    protected final int index;
    protected final StepAction action;
    
    protected StepStatus status = StepStatus.UNKNOWN;
    protected StepResult result;
    protected Instant createdAt = Instant.now();
    protected Instant updatedAt = Instant.now();
    protected Instant sentAt;
    protected Instant receivedAt;

    /**
     * Constructor with required fields.
     * 
     * STEP ID FORMAT: {orderId}:{index}:{action}:{serviceType}
     * Example: "12345:001:CHARGE_PAYMENT:PAYMENT"
     */
    protected AbstractSagaStep(long orderId, int index, StepAction action, String serviceType) {
        this.orderId = orderId;
        this.index = index;
        this.action = action;
        this.stepId = formatStepId(orderId, index, action, serviceType);
    }

    private String formatStepId(long orderId, int index, StepAction action, String serviceType) {
        return "%d:%03d:%s:%s".formatted(orderId, index, action.name(), serviceType);
    }

    // ============ TEMPLATE METHOD PATTERN ============

    /**
     * Execute this step with proper tracking and error handling.
     * 
     * WHY TEMPLATE METHOD:
     * - Ensures consistent logging and metrics
     * - Handles exceptions uniformly
     * - Subclasses focus only on business logic
     */
    @Override
    public final StepResult execute() {
        log.info("[Step:{}] Starting execution", stepId);
        sentAt = Instant.now();
        status = StepStatus.PROCESSING;

        try {
            // Call subclass implementation
            result = doExecute();
            
            // Update status based on result
            status = result.getStatus();
            receivedAt = Instant.now();
            updatedAt = Instant.now();

            log.info("[Step:{}] Execution completed with status: {}", stepId, status);
            return result;

        } catch (Exception e) {
            log.error("[Step:{}] Execution failed with exception", stepId, e);
            result = StepResult.fromException(e);
            status = StepStatus.FAILED;
            receivedAt = Instant.now();
            updatedAt = Instant.now();
            return result;
        }
    }

    /**
     * Subclasses implement actual execution logic here.
     * 
     * CONTRACT:
     * - Should NOT throw exceptions (return FAILED result instead)
     * - Should be IDEMPOTENT (safe to retry)
     * - Should handle timeouts gracefully
     */
    protected abstract StepResult doExecute();

    /**
     * Query status from external system with tracking.
     */
    @Override
    public final StepResult query() {
        log.info("[Step:{}] Querying status", stepId);
        try {
            var queryResult = doQuery();
            if (queryResult.getStatus() != StepStatus.UNKNOWN) {
                result = queryResult;
                status = queryResult.getStatus();
                updatedAt = Instant.now();
            }
            return queryResult;
        } catch (Exception e) {
            log.error("[Step:{}] Query failed", stepId, e);
            return StepResult.unknown();
        }
    }

    /**
     * Subclasses implement status query logic here.
     */
    protected abstract StepResult doQuery();

    // ============ STATUS UPDATE ============

    /**
     * Update status from external callback.
     * 
     * WHY CHECK CURRENT STATUS:
     * - Prevent overwriting already-final statuses
     * - Handle duplicate callbacks gracefully
     */
    @Override
    public boolean updateStatus(StepResult newResult) {
        // Don't update if already in final state
        if (status.isFinal()) {
            log.warn("[Step:{}] Ignoring update, already in final state: {}", stepId, status);
            return false;
        }

        log.info("[Step:{}] Updating status: {} -> {}", stepId, status, newResult.getStatus());
        this.result = newResult;
        this.status = newResult.getStatus();
        this.updatedAt = Instant.now();
        this.receivedAt = Instant.now();
        return true;
    }

    // ============ LOGGING ============

    /**
     * Convert to log format for persistence.
     */
    @Override
    public StepLog toLog() {
        return StepLog.builder()
            .stepId(stepId)
            .orderId(orderId)
            .index(index)
            .action(action)
            .status(status)
            .errorCode(result != null && result.getErrorCode() != null 
                ? result.getErrorCode().getCode() : null)
            .errorMessage(result != null ? result.getErrorMessage() : null)
            .externalRefId(result != null ? result.getExternalRefId() : null)
            .createdAt(createdAt)
            .updatedAt(updatedAt)
            .sentAt(sentAt)
            .receivedAt(receivedAt)
            .build();
    }
}
