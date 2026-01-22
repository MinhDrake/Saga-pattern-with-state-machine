package com.learning.saga.application.handler.hook;

import com.learning.saga.domain.model.saga.SagaContext;

/**
 * Hook interface for saga lifecycle callbacks.
 * 
 * WHY HOOKS:
 * ==========
 * Hooks provide extension points for cross-cutting concerns:
 * - Validation
 * - Deduplication
 * - Authorization
 * - Logging/Metrics
 * - Notification
 * 
 * HOOK TYPES:
 * ===========
 * 1. BEFORE hooks: Run before saga processing starts
 *    - Can REJECT the saga (return failure)
 *    - Typical uses: validation, dedup check
 * 
 * 2. AFTER hooks: Run after saga reaches terminal state
 *    - Cannot change saga outcome
 *    - Typical uses: cleanup, notification, metrics
 * 
 * DESIGN PRINCIPLE - SINGLE RESPONSIBILITY:
 * =========================================
 * Each hook does ONE thing:
 * - DuplicateCheckHook: Only checks for duplicates
 * - ValidationHook: Only validates input
 * - MetricsHook: Only records metrics
 * 
 * This makes hooks:
 * - Easy to test
 * - Easy to enable/disable
 * - Easy to understand
 */
public interface Hook {

    /**
     * Get the name of this hook (for logging/debugging).
     */
    String getName();

    /**
     * Execute this hook.
     * 
     * @param context The saga context
     * @return HookResult indicating success or failure
     */
    HookResult execute(SagaContext context);

    /**
     * Get the priority of this hook (lower = runs first).
     * 
     * Default priority is 100.
     * Use lower numbers for hooks that should run early (e.g., dedup: 10)
     * Use higher numbers for hooks that should run late (e.g., metrics: 200)
     */
    default int getPriority() {
        return 100;
    }

    /**
     * Check if this hook is enabled.
     * 
     * WHY: Allows conditional hook execution based on config.
     */
    default boolean isEnabled() {
        return true;
    }
}
