package com.minhdrake.saga.application.handler.hook;

import com.minhdrake.saga.domain.model.saga.SagaContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * HookContainer manages and provides hooks for saga processing.
 * 
 * WHY A CONTAINER:
 * ================
 * 1. CENTRALIZED REGISTRATION: All hooks registered in one place
 * 2. AUTO-DISCOVERY: Spring injects all Hook beans automatically
 * 3. ORDERING: Sorts hooks by priority
 * 4. FACTORY: Creates HookChain with appropriate hooks
 * 
 * HOW IT WORKS:
 * =============
 * 1. Spring discovers all @Component classes implementing Hook
 * 2. HookContainer receives them via @Autowired List<Hook>
 * 3. Hooks are sorted by priority (lower = runs first)
 * 4. createChain() builds a HookChain with all enabled hooks
 * 
 * EXTENSIBILITY:
 * ==============
 * To add a new hook:
 * 1. Create class implementing Hook
 * 2. Annotate with @Component
 * 3. Done! Spring auto-discovers and registers it
 * 
 * No changes needed to HookContainer or existing code.
 */
@Slf4j
@Component
public class HookContainer {

    private final List<Hook> beforeHooks;
    private final List<Hook> afterHooks;

    /**
     * Constructor with optional hook injection.
     * 
     * WHY OPTIONAL:
     * - Application may have no hooks configured
     * - Tests may not provide hooks
     * - Allows gradual hook addition
     */
    @Autowired(required = false)
    public HookContainer(List<Hook> allHooks) {
        this.beforeHooks = new ArrayList<>();
        this.afterHooks = new ArrayList<>();

        if (allHooks == null || allHooks.isEmpty()) {
            log.info("No hooks registered");
            return;
        }

        // Categorize and sort hooks
        for (Hook hook : allHooks) {
            if (!hook.isEnabled()) {
                log.debug("Hook {} is disabled, skipping", hook.getName());
                continue;
            }

            // For now, all hooks are "before" hooks
            // Could add annotation to distinguish before/after
            beforeHooks.add(hook);
            log.info("Registered hook: {} (priority: {})", hook.getName(), hook.getPriority());
        }

        // Sort by priority
        beforeHooks.sort(Comparator.comparingInt(Hook::getPriority));
        afterHooks.sort(Comparator.comparingInt(Hook::getPriority));

        log.info("HookContainer initialized with {} before hooks, {} after hooks",
                beforeHooks.size(), afterHooks.size());
    }

    /**
     * Default constructor for when no hooks are available.
     */
    public HookContainer() {
        this(null);
    }

    /**
     * Create a HookChain for the given context.
     * 
     * @param context The saga context
     * @return HookChain configured with appropriate hooks
     */
    public HookChain createChain(SagaContext context) {
        HookChain chain = new HookChain(context);

        beforeHooks.forEach(chain::addBeforeHook);
        afterHooks.forEach(chain::addAfterHook);

        return chain;
    }

    /**
     * Register a before hook manually (for testing or programmatic registration).
     */
    public void registerBeforeHook(Hook hook) {
        beforeHooks.add(hook);
        beforeHooks.sort(Comparator.comparingInt(Hook::getPriority));
        log.info("Manually registered before hook: {}", hook.getName());
    }

    /**
     * Register an after hook manually.
     */
    public void registerAfterHook(Hook hook) {
        afterHooks.add(hook);
        afterHooks.sort(Comparator.comparingInt(Hook::getPriority));
        log.info("Manually registered after hook: {}", hook.getName());
    }

    /**
     * Get count of registered before hooks.
     */
    public int getBeforeHookCount() {
        return beforeHooks.size();
    }

    /**
     * Get count of registered after hooks.
     */
    public int getAfterHookCount() {
        return afterHooks.size();
    }
}
