package com.minhdrake.saga.domain.model.type;

/**
 * StepStatus represents the execution status of an individual saga step.
 * 
 * WHY STEP STATUS VS ORDER STATUS:
 * ================================
 * - OrderStatus: Overall saga/order state machine
 * - StepStatus: Individual step execution result
 * 
 * A saga step goes through its own mini-lifecycle:
 * PENDING → EXECUTING → SUCCESS/FAILED
 * 
 * This separation allows:
 * 1. Fine-grained tracking of each step
 * 2. Retry logic per step
 * 3. Partial compensation (only compensate succeeded steps)
 * 
 * STEP LIFECYCLE:
 * ===============
 * 
 * PENDING ──► EXECUTING ──┬──► SUCCEEDED
 * │
 * └──► FAILED
 * │
 * └──► TIMEOUT
 * 
 * For compensation:
 * 
 * NEEDS_COMPENSATION ──► COMPENSATING ──┬──► COMPENSATED
 * │
 * └──► COMPENSATION_FAILED
 */
public enum StepStatus {

    // ============ FORWARD EXECUTION STATES ============

    /**
     * Step is waiting to be executed.
     * Initial state for all steps.
     */
    PENDING,

    /**
     * Step is currently executing.
     */
    EXECUTING,

    /**
     * Step completed successfully.
     */
    SUCCEEDED,

    /**
     * Step execution failed.
     */
    FAILED,

    /**
     * Step timed out before completing.
     */
    TIMEOUT,

    /**
     * Step was skipped (e.g., not applicable for this order).
     */
    SKIPPED,

    /**
     * Step result is unknown (timeout, network error during query).
     * Needs to be queried again during recovery.
     */
    UNKNOWN,

    /**
     * Step was completed (already finished - used during recovery).
     */
    COMPLETED,

    /**
     * Step was rejected (business rule violation, not a system error).
     */
    REJECTED,

    /**
     * Step is actively processing (in flight).
     */
    PROCESSING,

    // ============ COMPENSATION STATES ============

    /**
     * Step succeeded but saga failed - needs compensation.
     */
    NEEDS_COMPENSATION,

    /**
     * Compensation is in progress for this step.
     */
    COMPENSATING,

    /**
     * Compensation completed successfully.
     */
    COMPENSATED,

    /**
     * Compensation failed - requires manual intervention.
     */
    COMPENSATION_FAILED;

    /**
     * Check if this step completed successfully (forward or compensation).
     */
    public boolean isSuccess() {
        return this == SUCCEEDED || this == COMPENSATED;
    }

    /**
     * Check if this step is in a failed state.
     */
    public boolean isFailed() {
        return this == FAILED || this == TIMEOUT || this == COMPENSATION_FAILED;
    }

    /**
     * Check if this step is still in progress.
     */
    public boolean isInProgress() {
        return this == EXECUTING || this == COMPENSATING;
    }

    /**
     * Check if this step is in a terminal state.
     */
    public boolean isTerminal() {
        return switch (this) {
            case SUCCEEDED, FAILED, TIMEOUT, SKIPPED, COMPENSATED, COMPENSATION_FAILED -> true;
            default -> false;
        };
    }

    /**
     * Check if this step needs compensation when saga fails.
     * 
     * WHY: Only SUCCEEDED steps need compensation.
     * Failed/pending steps don't need to be undone.
     */
    public boolean needsCompensation() {
        return this == SUCCEEDED || this == NEEDS_COMPENSATION;
    }

    /**
     * Check if this step can be retried.
     */
    public boolean isRetryable() {
        return this == FAILED || this == TIMEOUT;
    }

    /**
     * Check if this is a final (non-changeable) status.
     * Alias for isTerminal() for compatibility.
     */
    public boolean isFinal() {
        return isTerminal();
    }
}
