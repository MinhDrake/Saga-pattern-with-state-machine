package com.learning.saga.domain.model.type;

/**
 * OrderStatus represents the possible states in the order saga state machine.
 * 
 * WHY A STATE MACHINE FOR ORDERS:
 * ===============================
 * An order goes through multiple steps (inventory, payment, shipping).
 * Each step can succeed, fail, or need compensation. The state machine:
 * 1. Tracks current progress
 * 2. Determines what happens next
 * 3. Ensures valid transitions only
 * 4. Supports recovery after failures
 * 
 * STATE CATEGORIES:
 * =================
 * 1. INITIAL: Order created, not yet processing
 * 2. PROCESSING: Forward execution in progress
 * 3. PENDING: Waiting for external response
 * 4. REVERTING: Compensation in progress
 * 5. TERMINAL: Final states (success or failure)
 * 
 * STATE DIAGRAM (simplified):
 * ===========================
 * 
 *    ┌─────────────────────────────────────────────────────────────┐
 *    │                                                             │
 *    │  INIT ──► PROCESSING ──► PENDING ──► PROCESSING ──► SUCCESS │
 *    │              │                           │                  │
 *    │              ▼                           ▼                  │
 *    │          REVERTING ◄───────────────── FAILED               │
 *    │              │                                              │
 *    │              ▼                                              │
 *    │          REVERTED                                          │
 *    │                                                             │
 *    └─────────────────────────────────────────────────────────────┘
 */
public enum OrderStatus {

    // ============ INITIAL STATES ============
    
    /**
     * Order created, ready to start processing.
     * This is the entry point for the state machine.
     */
    INIT,

    // ============ FORWARD PROCESSING STATES ============
    
    /**
     * Order is actively being processed (executing saga steps).
     * State handler: ProcessingStateHandler
     */
    PROCESSING,
    
    /**
     * Waiting for async response from external service.
     * State handler: PendingStateHandler
     */
    PENDING,
    
    /**
     * Resuming processing after recovery (server restart, etc.)
     * State handler: ResumingStateHandler
     */
    RESUMING,
    
    /**
     * Re-processing after a recoverable error.
     * State handler: ProcessingStateHandler (with retry logic)
     */
    RECOVERY_PROCESSING,

    // ============ REVERTING (COMPENSATION) STATES ============
    
    /**
     * Compensation is in progress (undoing completed steps).
     * State handler: RevertingStateHandler
     */
    REVERTING,
    
    /**
     * Waiting for async compensation response.
     * State handler: PendingStateHandler (in compensation mode)
     */
    REVERTING_PENDING,
    
    /**
     * Resuming compensation after recovery.
     * State handler: ResumingRevertStateHandler
     */
    RESUMING_REVERTING,
    
    /**
     * Recovery compensation processing.
     * State handler: RevertingStateHandler (with retry logic)
     */
    RECOVERY_REVERTING,

    // ============ TERMINAL STATES ============
    
    /**
     * Order completed successfully (all steps succeeded).
     * No more processing needed.
     */
    SUCCESS,
    
    /**
     * Order failed and no compensation was needed.
     * (e.g., first step failed, nothing to compensate)
     */
    FAILED,
    
    /**
     * All compensations completed successfully.
     * Order is rolled back to initial state.
     */
    REVERTED,
    
    /**
     * Compensation failed - requires manual intervention.
     * DANGER: System may be in inconsistent state!
     */
    REVERT_FAILED,
    
    /**
     * Order failed in a way that requires human review.
     * (e.g., partial success that can't be auto-compensated)
     */
    MANUAL_REVIEW,
    
    /**
     * Order timed out before completion.
     */
    TIMEOUT,
    
    /**
     * System error occurred (database failure, configuration error, etc.).
     * Requires technical investigation.
     */
    SYSTEM_ERROR;

    /**
     * Check if this is a terminal (final) state.
     * 
     * WHY: Terminal states need no further processing.
     * The state machine stops when reaching a terminal state.
     */
    public boolean isTerminal() {
        return switch (this) {
            case SUCCESS, FAILED, REVERTED, REVERT_FAILED, MANUAL_REVIEW, TIMEOUT, SYSTEM_ERROR -> true;
            default -> false;
        };
    }

    /**
     * Check if this is a processing state (forward execution).
     */
    public boolean isProcessing() {
        return switch (this) {
            case PROCESSING, RECOVERY_PROCESSING, RESUMING -> true;
            default -> false;
        };
    }

    /**
     * Check if this is a reverting state (compensation).
     */
    public boolean isReverting() {
        return switch (this) {
            case REVERTING, REVERTING_PENDING, RESUMING_REVERTING, RECOVERY_REVERTING -> true;
            default -> false;
        };
    }

    /**
     * Check if this is a pending/waiting state.
     */
    public boolean isPending() {
        return this == PENDING || this == REVERTING_PENDING;
    }

    /**
     * Check if this state indicates a failure.
     */
    public boolean isFailed() {
        return switch (this) {
            case FAILED, REVERT_FAILED, MANUAL_REVIEW, TIMEOUT -> true;
            default -> false;
        };
    }

    /**
     * Get the recovery state for this state.
     * 
     * WHY: After system restart, we resume from recovery states
     * which may have different logic (e.g., query before execute).
     */
    public OrderStatus getRecoveryState() {
        return switch (this) {
            case PROCESSING, PENDING -> RECOVERY_PROCESSING;
            case REVERTING, REVERTING_PENDING -> RECOVERY_REVERTING;
            case RESUMING -> RECOVERY_PROCESSING;
            case RESUMING_REVERTING -> RECOVERY_REVERTING;
            default -> this;
        };
    }

    /**
     * Get the resume state for this state.
     * 
     * WHY: When resuming (e.g., from callback), we need to transition
     * to the appropriate resuming state.
     */
    public OrderStatus toResumeStatus() {
        return switch (this) {
            case PENDING, PROCESSING -> RESUMING;
            case REVERTING_PENDING, REVERTING -> RESUMING_REVERTING;
            default -> this;
        };
    }
}
