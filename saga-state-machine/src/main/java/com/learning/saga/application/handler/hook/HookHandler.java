package com.learning.saga.application.handler.hook;

import com.learning.saga.domain.model.saga.SagaContext;
import lombok.Getter;

/**
 * HookHandler is the base class for all hook implementations.
 * 
 * WHY ABSTRACT CLASS (not interface):
 * ===================================
 * 1. Default implementations for doBefore/doAfter
 * 2. Shared behavior (delegation to chain)
 * 3. Hooks can override just what they need
 * 
 * CHAIN OF RESPONSIBILITY:
 * ========================
 * Hooks form a chain. Each hook:
 * 1. Executes its logic
 * 2. If successful, calls chain.doBefore/doAfter to continue
 * 3. If failed, returns early (chain stops)
 * 
 * EXAMPLE:
 * ========
 * {@code
 * public class DuplicateCheckHook extends HookHandler {
 *     public HookResult doBefore(SagaContext ctx, HookChain chain) {
 *         if (isDuplicate(ctx.getOrderNo())) {
 *             return HookResult.duplicate("Order already exists");
 *         }
 *         return chain.executeBefore(ctx); // Continue chain
 *     }
 *     
 *     public HookType getType() {
 *         return HookType.DUPLICATE_CHECK;
 *     }
 * }
 * }
 * 
 * WHY PASS CHAIN TO HOOK:
 * =======================
 * This allows hooks to:
 * 1. Execute before/after their logic runs
 * 2. Conditionally skip remaining hooks
 * 3. Transform context before passing to next hook
 */
@Getter
public abstract class HookHandler {

    /**
     * Execute before saga processing starts.
     * 
     * DEFAULT: Just continue the chain.
     * OVERRIDE: Add validation/transformation logic.
     * 
     * @param context The saga context
     * @param chain The hook chain for delegation
     * @return HookResult indicating success or failure
     */
    public HookResult doBefore(SagaContext context, HookChain chain) {
        // Default: just continue the chain
        if (chain != null) {
            return chain.executeBefore(context);
        }
        return HookResult.success(getType());
    }

    /**
     * Execute after saga reaches terminal state.
     * 
     * DEFAULT: Just continue the chain.
     * OVERRIDE: Add notification/logging logic.
     * 
     * @param context The saga context (with final status)
     * @param chain The hook chain for delegation
     * @return HookResult indicating success or failure
     */
    public HookResult doAfter(SagaContext context, HookChain chain) {
        // Default: just continue the chain
        if (chain != null) {
            HookResult result = chain.executeAfter(context);
            return result != null ? result : HookResult.success(getType());
        }
        return HookResult.success(getType());
    }

    /**
     * Get the type of this hook.
     * Used for registration and logging.
     */
    public abstract HookType getType();
}
