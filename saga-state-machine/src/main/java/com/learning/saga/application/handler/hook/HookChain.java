package com.learning.saga.application.handler.hook;

import com.learning.saga.domain.model.saga.SagaContext;

/**
 * HookChain manages the execution of a sequence of hooks.
 * 
 * CHAIN OF RESPONSIBILITY PATTERN:
 * ================================
 * Instead of calling hooks directly, we use a chain:
 * 
 * Hook1.doBefore() → Hook2.doBefore() → Hook3.doBefore() → ...
 * 
 * Each hook calls chain.executeBefore() to continue to the next.
 * If a hook fails, it returns early and the chain stops.
 * 
 * WHY A CHAIN:
 * ============
 * 1. ORDERING: Hooks execute in defined order
 * 2. SHORT-CIRCUIT: If one fails, rest don't run
 * 3. METRICS: Can track time for each hook
 * 4. DECOUPLING: Hooks don't know about each other
 * 
 * THREAD SAFETY:
 * ==============
 * Each request gets its OWN HookChain instance.
 * This is called "Thread Confinement" - the chain is not shared.
 * Therefore, no synchronization is needed.
 * 
 * REQUEST FLOW:
 * =============
 * Request → Create new HookChain → Execute hooks → Discard chain
 * Next request → Create NEW HookChain → ...
 */
public interface HookChain {

    /**
     * Execute the next "before" hook in the chain.
     * 
     * Called by hooks to continue the chain after their logic.
     * 
     * @param context The saga context
     * @return Result from the remaining chain
     */
    HookResult executeBefore(SagaContext context);

    /**
     * Execute the next "after" hook in the chain.
     * 
     * @param context The saga context
     * @return Result from the remaining chain
     */
    HookResult executeAfter(SagaContext context);
}
