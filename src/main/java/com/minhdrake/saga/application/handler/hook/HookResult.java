package com.minhdrake.saga.application.handler.hook;

import com.minhdrake.saga.domain.model.saga.StepResult;
import com.minhdrake.saga.domain.model.type.ErrorCode;
import com.minhdrake.saga.domain.model.type.StepStatus;
import lombok.Builder;
import lombok.Getter;

/**
 * HookResult encapsulates the outcome of hook execution.
 * 
 * WHY HOOK RESULTS:
 * =================
 * Hooks are validation/preparation steps that run BEFORE the saga starts.
 * The result tells us whether to proceed or reject the saga.
 * 
 * FAILURE TYPES:
 * ==============
 * Different failure types lead to different handling:
 * - DUPLICATE: Saga already exists → silently ignore or return existing ID
 * - VALIDATION: Input invalid → return error to caller
 * - AUTHORIZATION: Not allowed → return 403-equivalent error
 * 
 * Unlike step results, hook results don't have PENDING state
 * because hooks should execute synchronously.
 */
@Getter
@Builder
public class HookResult {

    /**
     * Whether the hook passed (saga can proceed).
     */
    private final boolean success;

    /**
     * Type of failure if hook failed.
     */
    private final FailureType failureType;

    /**
     * Error code for failed hooks.
     */
    private final ErrorCode errorCode;

    /**
     * Human-readable error message.
     */
    private final String errorMessage;

    /**
     * Additional metadata from the hook (e.g., existing order ID for duplicates).
     */
    private final String metadata;

    /**
     * Types of hook failures.
     */
    public enum FailureType {
        /** No failure - hook passed */
        NONE,

        /** Duplicate saga detected */
        DUPLICATE,

        /** Input validation failed */
        VALIDATION,

        /** Authorization/permission check failed */
        AUTHORIZATION,

        /** System error during hook execution */
        SYSTEM_ERROR,

        /** Generic failure */
        OTHER
    }

    // ============ FACTORY METHODS ============

    /**
     * Create a success result.
     */
    public static HookResult success() {
        return HookResult.builder()
                .success(true)
                .failureType(FailureType.NONE)
                .build();
    }

    /**
     * Create a duplicate failure result.
     */
    public static HookResult duplicate(String existingOrderInfo) {
        return HookResult.builder()
                .success(false)
                .failureType(FailureType.DUPLICATE)
                .errorCode(ErrorCode.DUPLICATE_REQUEST)
                .errorMessage("Duplicate order detected")
                .metadata(existingOrderInfo)
                .build();
    }

    /**
     * Create a validation failure result.
     */
    public static HookResult validationFailed(String message) {
        return HookResult.builder()
                .success(false)
                .failureType(FailureType.VALIDATION)
                .errorCode(ErrorCode.INVALID_INPUT)
                .errorMessage(message)
                .build();
    }

    /**
     * Create an authorization failure result.
     */
    public static HookResult unauthorized(String message) {
        return HookResult.builder()
                .success(false)
                .failureType(FailureType.AUTHORIZATION)
                .errorCode(ErrorCode.INVALID_STATE) // Could add AUTH_FAILED to ErrorCode
                .errorMessage(message)
                .build();
    }

    /**
     * Create a system error result.
     */
    public static HookResult systemError(String message) {
        return HookResult.builder()
                .success(false)
                .failureType(FailureType.SYSTEM_ERROR)
                .errorCode(ErrorCode.INTERNAL_ERROR)
                .errorMessage(message)
                .build();
    }

    /**
     * Convert this hook result to a StepResult for storage.
     * 
     * WHY: SagaContext.lastResult is a StepResult, so we need to convert.
     */
    public StepResult toStepResult() {
        if (success) {
            return StepResult.success();
        }
        return StepResult.builder()
                .status(StepStatus.FAILED)
                .errorCode(errorCode)
                .errorMessage(errorMessage)
                .build();
    }
}
