package com.learning.saga.domain.model.saga;

import com.learning.saga.domain.model.type.ErrorCode;
import com.learning.saga.domain.model.type.StepStatus;
import lombok.Builder;
import lombok.Data;

/**
 * StepResult encapsulates the outcome of a saga step execution.
 * 
 * WHY A DEDICATED RESULT CLASS:
 * =============================
 * 1. RICH INFORMATION: Contains status, error details, and metadata
 * 2. IMMUTABILITY: Results don't change after creation
 * 3. SERIALIZABLE: Easy to persist and transmit
 * 
 * USAGE:
 * ======
 * - Step.execute() returns StepResult
 * - Callbacks provide StepResult
 * - Query operations return StepResult
 */
@Data
@Builder
public class StepResult {

    /**
     * The execution status of the step.
     */
    private final StepStatus status;

    /**
     * Error code if the step failed.
     */
    private final ErrorCode errorCode;

    /**
     * Human-readable error message for debugging.
     */
    private final String errorMessage;

    /**
     * External reference ID (e.g., payment transaction ID).
     * Useful for reconciliation and debugging.
     */
    private final String externalRefId;

    /**
     * Additional metadata as JSON string.
     * Allows steps to pass arbitrary data.
     */
    private final String metadata;

    // ============ FACTORY METHODS ============

    /**
     * Create a successful result.
     */
    public static StepResult success() {
        return StepResult.builder()
            .status(StepStatus.SUCCEEDED)
            .errorCode(ErrorCode.SUCCESS)
            .build();
    }

    /**
     * Create a successful result with external reference.
     */
    public static StepResult success(String externalRefId) {
        return StepResult.builder()
            .status(StepStatus.SUCCEEDED)
            .errorCode(ErrorCode.SUCCESS)
            .externalRefId(externalRefId)
            .build();
    }

    /**
     * Create a failed result.
     */
    public static StepResult failed(ErrorCode errorCode, String message) {
        return StepResult.builder()
            .status(StepStatus.FAILED)
            .errorCode(errorCode)
            .errorMessage(message)
            .build();
    }

    /**
     * Create a pending result (waiting for async callback).
     */
    public static StepResult pending(String externalRefId) {
        return StepResult.builder()
            .status(StepStatus.PENDING)
            .externalRefId(externalRefId)
            .build();
    }

    /**
     * Create an unknown result (couldn't determine status).
     */
    public static StepResult unknown() {
        return StepResult.builder()
            .status(StepStatus.UNKNOWN)
            .build();
    }

    /**
     * Create a rejected result (business rule violation).
     */
    public static StepResult rejected(ErrorCode errorCode, String message) {
        return StepResult.builder()
            .status(StepStatus.REJECTED)
            .errorCode(errorCode)
            .errorMessage(message)
            .build();
    }

    /**
     * Create a result from an exception.
     * 
     * WHY: Steps should NOT throw exceptions. Instead, catch and convert
     * to StepResult for proper handling by the saga engine.
     */
    public static StepResult fromException(Exception e) {
        return StepResult.builder()
            .status(StepStatus.FAILED)
            .errorCode(ErrorCode.INTERNAL_ERROR)
            .errorMessage(e.getMessage())
            .build();
    }

    // ============ HELPER METHODS ============

    /**
     * Check if this result indicates success.
     */
    public boolean isSuccess() {
        return status == StepStatus.SUCCEEDED;
    }

    /**
     * Check if this result indicates the step should be retried.
     */
    public boolean isRetryable() {
        return errorCode != null && errorCode.isRetryable();
    }

    /**
     * Check if the saga should continue processing.
     */
    public boolean shouldContinue() {
        return status == StepStatus.SUCCEEDED || status == StepStatus.COMPLETED;
    }

    /**
     * Check if the saga should wait for callback.
     */
    public boolean shouldWait() {
        return status == StepStatus.PENDING || status == StepStatus.UNKNOWN;
    }
}
