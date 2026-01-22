package com.learning.saga.domain.exception;

import com.learning.saga.domain.model.type.ErrorCode;
import lombok.Getter;

/**
 * Base exception for all saga-related errors.
 * 
 * WHY CUSTOM EXCEPTION:
 * =====================
 * 1. STRUCTURED ERRORS: All exceptions have an ErrorCode
 * 2. CONSISTENT HANDLING: Single exception type to catch
 * 3. RICH CONTEXT: Can include saga/step context
 * 
 * USAGE:
 * ======
 * - Thrown by saga engine when state transitions fail
 * - Thrown by state handlers for invalid operations
 * - Caught and converted to appropriate responses
 */
@Getter
public class SagaException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Long orderId;
    private final String stepId;

    public SagaException(ErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.orderId = null;
        this.stepId = null;
    }

    public SagaException(ErrorCode errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
        this.orderId = null;
        this.stepId = null;
    }

    public SagaException(ErrorCode errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
        this.orderId = null;
        this.stepId = null;
    }

    public SagaException(ErrorCode errorCode, long orderId) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.orderId = orderId;
        this.stepId = null;
    }

    public SagaException(ErrorCode errorCode, long orderId, String stepId, String message) {
        super(message);
        this.errorCode = errorCode;
        this.orderId = orderId;
        this.stepId = stepId;
    }

    /**
     * Create exception for state handler not found.
     */
    public static SagaException stateHandlerNotFound(String status) {
        return new SagaException(
            ErrorCode.STATE_HANDLER_NOT_FOUND,
            "No handler registered for status: " + status
        );
    }

    /**
     * Create exception for invalid state transition.
     */
    public static SagaException invalidTransition(String from, String to) {
        return new SagaException(
            ErrorCode.INVALID_STATE_TRANSITION,
            "Invalid transition from %s to %s".formatted(from, to)
        );
    }

    /**
     * Create exception for step execution failure.
     */
    public static SagaException stepFailed(long orderId, String stepId, String reason) {
        return new SagaException(
            ErrorCode.STEP_EXECUTION_FAILED,
            orderId,
            stepId,
            "Step %s failed: %s".formatted(stepId, reason)
        );
    }
}
