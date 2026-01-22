package com.learning.saga.domain.model.type;

/**
 * ErrorCode provides standardized error codes for the saga system.
 * 
 * WHY STANDARDIZED ERROR CODES:
 * =============================
 * 1. CONSISTENCY: All components speak the same "error language"
 * 2. MAPPING: Can map to HTTP status codes, gRPC codes, etc.
 * 3. MONITORING: Easy to aggregate and alert on specific error types
 * 4. DEBUGGING: Error codes are more searchable than messages
 * 
 * ERROR CODE STRUCTURE:
 * =====================
 * - 1xxx: Client/Input errors
 * - 2xxx: Business logic errors
 * - 3xxx: External service errors
 * - 4xxx: Internal/System errors
 * - 5xxx: Saga-specific errors
 */
public enum ErrorCode {

    // ============ SUCCESS ============
    SUCCESS(0, "Success"),

    // ============ CLIENT ERRORS (1xxx) ============
    INVALID_INPUT(1001, "Invalid input parameters"),
    INVALID_STATE(1002, "Invalid state for this operation"),
    DUPLICATE_REQUEST(1003, "Duplicate request detected"),
    NOT_FOUND(1004, "Resource not found"),

    // ============ BUSINESS ERRORS (2xxx) ============
    INSUFFICIENT_INVENTORY(2001, "Not enough inventory"),
    INSUFFICIENT_BALANCE(2002, "Insufficient account balance"),
    PAYMENT_DECLINED(2003, "Payment was declined"),
    ORDER_CANCELLED(2004, "Order has been cancelled"),

    // ============ EXTERNAL SERVICE ERRORS (3xxx) ============
    PAYMENT_SERVICE_ERROR(3001, "Payment service unavailable"),
    INVENTORY_SERVICE_ERROR(3002, "Inventory service unavailable"),
    SHIPPING_SERVICE_ERROR(3003, "Shipping service unavailable"),
    EXTERNAL_TIMEOUT(3004, "External service timeout"),

    // ============ INTERNAL ERRORS (4xxx) ============
    DATABASE_ERROR(4001, "Database operation failed"),
    INTERNAL_ERROR(4002, "Internal server error"),
    CONFIGURATION_ERROR(4003, "Configuration error"),

    // ============ SAGA ERRORS (5xxx) ============
    STATE_HANDLER_NOT_FOUND(5001, "No handler found for state"),
    STEP_EXECUTION_FAILED(5002, "Saga step execution failed"),
    COMPENSATION_FAILED(5003, "Compensation step failed"),
    SAGA_TIMEOUT(5004, "Saga execution timed out"),
    INVALID_STATE_TRANSITION(5005, "Invalid state transition attempted");

    private final int code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    /**
     * Check if this error is retryable.
     * 
     * WHY: Some errors (like timeouts) can be retried,
     * while others (like insufficient balance) should not be retried.
     */
    public boolean isRetryable() {
        return switch (this) {
            case PAYMENT_SERVICE_ERROR, 
                 INVENTORY_SERVICE_ERROR, 
                 SHIPPING_SERVICE_ERROR,
                 EXTERNAL_TIMEOUT,
                 DATABASE_ERROR -> true;
            default -> false;
        };
    }

    /**
     * Check if this error requires compensation.
     * 
     * WHY: Business errors (like declined payment) need compensation
     * for previously completed steps, while input errors don't.
     */
    public boolean requiresCompensation() {
        int codeGroup = code / 1000;
        return codeGroup == 2 || codeGroup == 3; // Business or external errors
    }
}
