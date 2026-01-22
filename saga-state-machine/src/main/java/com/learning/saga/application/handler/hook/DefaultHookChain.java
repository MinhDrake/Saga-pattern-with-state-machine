package com.learning.saga.application.handler.hook;

import com.learning.saga.domain.model.saga.SagaContext;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * DefaultHookChain implements the Chain of Responsibility pattern for hooks.
 * 
 * HOW IT WORKS:
 * =============
 * 1. Chain is created with ordered list of HookHandlers
 * 2. executeBefore() called → runs first hook's doBefore()
 * 3. Hook calls chain.executeBefore() → advances position, runs next hook
 * 4. When all hooks run (or one fails), chain returns result
 * 
 * POSITION TRACKING:
 * ==================
 * The chain tracks which hook to execute next via `position`.
 * - position = 0 → run handlers[0]
 * - position = 1 → run handlers[1]
 * - position = handlers.size() → all done, return success
 * 
 * WHY NOT USE ITERATOR:
 * =====================
 * Could use Iterator, but position-based approach:
 * - Allows for debugging (know exact position)
 * - Supports reset if needed
 * - Simpler to understand
 * 
 * THREAD CONFINEMENT:
 * ===================
 * Each request gets its OWN DefaultHookChain instance.
 * Position is private to that instance.
 * No synchronization needed!
 */
@Slf4j
public class DefaultHookChain implements HookChain {

    /**
     * Ordered list of hook handlers to execute.
     */
    private final List<HookHandler> handlers;

    /**
     * Current position in the chain for "before" hooks.
     */
    private int beforePosition = 0;

    /**
     * Current position in the chain for "after" hooks.
     */
    private int afterPosition = 0;

    /**
     * Create a chain with the given handlers.
     * 
     * @param handlers Ordered list of handlers to execute
     */
    public DefaultHookChain(List<HookHandler> handlers) {
        this.handlers = handlers;
    }

    /**
     * Execute the next "before" hook in the chain.
     * 
     * LOGIC:
     * 1. If no more hooks, return success
     * 2. Get current hook, advance position
     * 3. Call hook's doBefore() - hook may call chain.executeBefore()
     * 4. Return hook's result
     */
    @Override
    public HookResult executeBefore(SagaContext context) {
        // Check if all hooks have been executed
        if (beforePosition >= handlers.size()) {
            log.debug("[HookChain] All before hooks completed");
            return HookResult.success();
        }

        // Get the current hook and advance position
        HookHandler handler = handlers.get(beforePosition++);
        
        log.debug("[HookChain] Executing before hook: {} (position: {})", 
            handler.getType(), beforePosition - 1);

        try {
            // Execute the hook - it may call chain.executeBefore() to continue
            HookResult result = handler.doBefore(context, this);
            
            if (!result.isSuccess()) {
                log.warn("[HookChain] Before hook {} failed: {}", 
                    handler.getType(), result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("[HookChain] Before hook {} threw exception", handler.getType(), e);
            return HookResult.fromException(e);
        }
    }

    /**
     * Execute the next "after" hook in the chain.
     * 
     * DIFFERENCE FROM BEFORE:
     * - After hooks run even if previous after-hooks failed
     * - After hooks are for cleanup/notification, not validation
     */
    @Override
    public HookResult executeAfter(SagaContext context) {
        // Check if all hooks have been executed
        if (afterPosition >= handlers.size()) {
            log.debug("[HookChain] All after hooks completed");
            return HookResult.success();
        }

        // Get the current hook and advance position
        HookHandler handler = handlers.get(afterPosition++);
        
        log.debug("[HookChain] Executing after hook: {} (position: {})", 
            handler.getType(), afterPosition - 1);

        try {
            // Execute the hook
            HookResult result = handler.doAfter(context, this);
            
            // For after hooks, log failures but continue
            if (!result.isSuccess()) {
                log.warn("[HookChain] After hook {} failed (continuing): {}", 
                    handler.getType(), result.getErrorMessage());
            }
            
            return result;
            
        } catch (Exception e) {
            log.error("[HookChain] After hook {} threw exception (continuing)", handler.getType(), e);
            // Continue to next hook even on exception
            return executeAfter(context);
        }
    }

    /**
     * Get the current position for debugging.
     */
    public int getBeforePosition() {
        return beforePosition;
    }

    /**
     * Get the total number of handlers.
     */
    public int getHandlerCount() {
        return handlers.size();
    }
}
