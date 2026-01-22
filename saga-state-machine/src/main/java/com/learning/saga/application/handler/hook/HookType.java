package com.learning.saga.application.handler.hook;

/**
 * HookType defines the types of hooks that can be executed during saga lifecycle.
 * 
 * WHY HOOKS:
 * ==========
 * Hooks allow adding cross-cutting concerns without modifying core saga logic:
 * - Validation before processing
 * - Notifications after completion
 * - Logging and metrics
 * - Business-specific logic
 * 
 * HOOK EXECUTION PHASES:
 * ======================
 * 1. BEFORE: Runs before saga starts processing (in INIT state)
 * 2. AFTER: Runs after saga reaches terminal state
 * 
 * HOOK ORDERING:
 * ==============
 * Hooks are executed in a defined order. For BEFORE hooks:
 * DUPLICATE_CHECK → VALIDATION → AUTHORIZATION → ...
 * 
 * If any BEFORE hook fails, saga doesn't start.
 * If any AFTER hook fails, saga is still considered complete.
 */
public enum HookType {

    // ============ BEFORE HOOKS (run before saga starts) ============
    
    /**
     * Check if this order is a duplicate (already processing or completed).
     * 
     * WHY FIRST: Duplicate check is cheap and avoids wasted work.
     */
    DUPLICATE_CHECK,

    /**
     * Validate the order data (amounts, items, addresses, etc.)
     */
    VALIDATION,

    /**
     * Check if the user is authorized for this operation.
     */
    AUTHORIZATION,

    /**
     * Map/transform the input data before processing.
     */
    DATA_MAPPING,

    // ============ AFTER HOOKS (run after saga completes) ============

    /**
     * Send notification to customer (email, SMS, push).
     */
    NOTIFICATION,

    /**
     * Log the transaction for analytics and audit.
     */
    TRANSACTION_LOG,

    /**
     * Update external systems about the result.
     */
    EXTERNAL_SYNC,

    /**
     * Clean up temporary data or resources.
     */
    CLEANUP,

    // ============ SPECIAL ============

    /**
     * No-op hook for testing or placeholder.
     */
    NOOP
}
