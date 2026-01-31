package com.minhdrake.saga.application.handler.hook;

import com.minhdrake.saga.domain.model.saga.SagaContext;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * HookChain manages the execution of multiple hooks in sequence.
 * 
 * WHY A CHAIN:
 * ============
 * Multiple hooks may need to run (validation, dedup, authorization).
 * The chain:
 * 1. Executes hooks in order
 * 2. Stops at first failure
 * 3. Provides consistent result handling
 * 
 * CHAIN OF RESPONSIBILITY PATTERN:
 * ================================
 * Similar to servlet filters or Spring interceptors.
 * Each hook can:
 * - Pass: Continue to next hook
 * - Fail: Stop chain, return failure
 * 
 * EXECUTION ORDER:
 * ================
 * Hooks are executed in the order they were added.
 * Typically:
 * 1. Duplicate check (fast, avoids wasted work)
 * 2. Validation (check input)
 * 3. Authorization (check permissions)
 * 4. Preparation (enrich data, set defaults)
 */
@Slf4j
public class HookChain {

    private final List<Hook> beforeHooks;
    private final List<Hook> afterHooks;
    private final SagaContext context;

    public HookChain(SagaContext context) {
        this.context = context;
        this.beforeHooks = new ArrayList<>();
        this.afterHooks = new ArrayList<>();
    }

    /**
     * Add a "before" hook (runs before saga processing).
     */
    public HookChain addBeforeHook(Hook hook) {
        beforeHooks.add(hook);
        return this;
    }

    /**
     * Add an "after" hook (runs after saga processing).
     */
    public HookChain addAfterHook(Hook hook) {
        afterHooks.add(hook);
        return this;
    }

    /**
     * Execute all "before" hooks.
     * 
     * STOPS AT FIRST FAILURE:
     * If any hook fails, subsequent hooks are NOT executed.
     * This is intentional - no point validating if already duplicate.
     * 
     * @param context The saga context
     * @return HookResult from the failing hook, or success if all pass
     */
    public HookResult executeBefore(SagaContext context) {
        log.debug("Executing {} before hooks for order {}",
                beforeHooks.size(), context.getOrderId());

        for (Hook hook : beforeHooks) {
            try {
                log.debug("Running hook: {}", hook.getName());
                HookResult result = hook.execute(context);

                if (!result.isSuccess()) {
                    log.warn("Hook {} failed for order {}: {}",
                            hook.getName(), context.getOrderId(), result.getErrorMessage());
                    return result;
                }

                log.debug("Hook {} passed", hook.getName());

            } catch (Exception e) {
                log.error("Hook {} threw exception for order {}",
                        hook.getName(), context.getOrderId(), e);
                return HookResult.systemError("Hook failed: " + e.getMessage());
            }
        }

        log.debug("All before hooks passed for order {}", context.getOrderId());
        return HookResult.success();
    }

    /**
     * Execute all "after" hooks.
     * 
     * DIFFERENCE FROM BEFORE:
     * - After hooks run even if saga failed (for cleanup/notification)
     * - After hook failures are logged but don't change saga status
     * 
     * @param context The saga context (now with final status)
     */
    public void executeAfter(SagaContext context) {
        log.debug("Executing {} after hooks for order {}",
                afterHooks.size(), context.getOrderId());

        for (Hook hook : afterHooks) {
            try {
                log.debug("Running after hook: {}", hook.getName());
                hook.execute(context);

            } catch (Exception e) {
                // Log but don't fail - after hooks are best-effort
                log.error("After hook {} failed for order {}",
                        hook.getName(), context.getOrderId(), e);
            }
        }

        log.debug("After hooks completed for order {}", context.getOrderId());
    }

    /**
     * Get the count of before hooks.
     */
    public int getBeforeHookCount() {
        return beforeHooks.size();
    }

    /**
     * Get the count of after hooks.
     */
    public int getAfterHookCount() {
        return afterHooks.size();
    }
}
