package com.learning.saga.application.handler.hook;

import com.learning.saga.domain.model.saga.StepResult;
import com.learning.saga.domain.model.type.ErrorCode;
import com.learning.saga.domain.model.type.StepStatus;
import lombok.Builder;
import lombok.Data;

/**
 * HookResult encapsulates the outcome of hook execution.
 * 
 * WHY SEPARATE FROM StepResult:
 * =============================
 * Hooks are different from saga steps:
 * - Steps interact with external services
 * - Hooks are internal validations/transformations
 * - Hooks have different failure types (DUPLICATE vs VALIDATION)
 * 
 * However, we can convert HookResult to StepResult when needed
 * for consistent error handling.
 */
@Data
@Builder
public class HookResult {

    /**
     * Whether the hook executed successfully.
     */
    private final boolean success;

    /**
     * Type of failure (if not successful).
     */
    private final FailureType failureType;

    /**
     * Error message for debugging.
     */
    private final String errorMessage;

    /**
     * The hook type that produced this result.
     */
    private final HookType hookType;

    /**
     * Types of hook failures.
     */
    public enum FailureType {
        NONE,           // No failure (success)
        DUPLICATE,      // Request is a duplicate
        VALIDATION,     // Input validation failed
        AUTHORIZATION,  // User not authorized
        SYSTEM_ERROR    // Unexpected error in hook
    }

    // ============ FACTORY METHODS ============

    public static HookResult success() {
        return HookResult.builder()
            .success(true)
            .failureType(FailureType.NONE)
            .build();
    }

    public static HookResult success(HookType hookType) {
        return HookResult.builder()
            .success(true)
            .failureType(FailureType.NONE)
            .hookType(hookType)
            .build();
    }

    public static HookResult duplicate(String message) {
        return HookResult.builder()
            .success(false)
            .failureType(FailureType.DUPLICATE)
            .errorMessage(message)
            .build();
    }

    public static HookResult validationFailed(String message) {
        return HookResult.builder()
            .success(false)
            .failureType(FailureType.VALIDATION)
            .errorMessage(message)
            .build();
    }

    public static HookResult unauthorized(String message) {
        return HookResult.builder()
            .success(false)
            .failureType(FailureType.AUTHORIZATION)
            .errorMessage(message)
            .build();
    }

    public static HookResult systemError(String message) {
        return HookResult.builder()
            .success(false)
            .failureType(FailureType.SYSTEM_ERROR)
            .errorMessage(message)
            .build();
    }

    public static HookResult fromException(Exception e) {
        return HookResult.builder()
            .success(false)
            .failureType(FailureType.SYSTEM_ERROR)
            .errorMessage(e.getMessage())
            .build();
    }

    // ============ CONVERSION ============

    /**
     * Convert to StepResult for consistent error handling.
     */
    public StepResult toStepResult() {
        if (success) {
            return StepResult.success();
        }

        ErrorCode errorCode = switch (failureType) {
            case DUPLICATE -> ErrorCode.DUPLICATE_REQUEST;
            case VALIDATION -> ErrorCode.INVALID_INPUT;
            case AUTHORIZATION -> ErrorCode.INVALID_INPUT; // or create specific code
            case SYSTEM_ERROR -> ErrorCode.INTERNAL_ERROR;
            default -> ErrorCode.INTERNAL_ERROR;
        };

        return StepResult.failed(errorCode, errorMessage);
    }
}
