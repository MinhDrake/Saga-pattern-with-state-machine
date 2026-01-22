package com.learning.saga.application.handler.hook;

import com.learning.saga.domain.model.saga.SagaContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * HookContainer manages hook registration and chain creation.
 * 
 * WHY A CONTAINER:
 * ================
 * 1. AUTO-REGISTRATION: Hooks are discovered via Spring DI
 * 2. CHAIN FACTORY: Creates new HookChain for each request
 * 3. HOOK LOOKUP: Can find hooks by type if needed
 * 
 * AUTO-REGISTRATION:
 * ==================
 * Spring automatically injects all HookHandler beans into the constructor.
 * We build a map of HookType -> HookHandler for lookup.
 * 
 * THREAD SAFETY:
 * ==============
 * - Container itself is a SINGLETON (one instance)
 * - But createChain() returns a NEW chain for each request
 * - Each chain is confined to one thread
 * - Therefore, no race conditions
 * 
 * DEFAULT HOOK ORDER:
 * ===================
 * The order hooks execute is defined by DEFAULT_HOOK_ORDER.
 * This can be customized per request if needed.
 */
@Slf4j
@Component
public class HookContainer {

    /**
     * Default order of hooks for processing.
     */
    private static final List<HookType> DEFAULT_HOOK_ORDER = List.of(
        HookType.DUPLICATE_CHECK,
        HookType.VALIDATION,
        HookType.AUTHORIZATION,
        HookType.DATA_MAPPING,
        HookType.NOTIFICATION,
        HookType.TRANSACTION_LOG,
        HookType.EXTERNAL_SYNC,
        HookType.CLEANUP
    );

    /**
     * Map of HookType -> HookHandler.
     */
    private final Map<HookType, HookHandler> hookHandlers;

    /**
     * No-op handler for missing hooks.
     */
    private static final HookHandler NOOP_HANDLER = new NoOpHookHandler();

    /**
     * Constructor - Spring injects all HookHandler beans.
     */
    public HookContainer(List<HookHandler> handlers) {
        this.hookHandlers = new HashMap<>();
        
        for (HookHandler handler : handlers) {
            log.info("[HookContainer] Registering hook: {}", handler.getType());
            hookHandlers.put(handler.getType(), handler);
        }
        
        log.info("[HookContainer] Registered {} hooks", hookHandlers.size());
    }

    /**
     * Create a new HookChain for processing a saga.
     * 
     * WHY NEW CHAIN EACH TIME:
     * - Thread Confinement: Each request gets its own chain
     * - No shared state: No synchronization needed
     * - Clean state: Position starts at 0
     * 
     * @param context The saga context (can be used for context-specific hooks)
     * @return A new HookChain instance
     */
    public HookChain createChain(SagaContext context) {
        List<HookHandler> orderedHandlers = DEFAULT_HOOK_ORDER.stream()
            .map(type -> hookHandlers.getOrDefault(type, NOOP_HANDLER))
            .collect(Collectors.toList());

        return new DefaultHookChain(orderedHandlers);
    }

    /**
     * Create a chain with custom hook types.
     * Useful for flows that need different hooks.
     */
    public HookChain createChain(List<HookType> hookTypes) {
        List<HookHandler> handlers = hookTypes.stream()
            .map(type -> hookHandlers.getOrDefault(type, NOOP_HANDLER))
            .collect(Collectors.toList());

        return new DefaultHookChain(handlers);
    }

    /**
     * Get a specific hook handler by type.
     */
    public HookHandler getHandler(HookType type) {
        return hookHandlers.getOrDefault(type, NOOP_HANDLER);
    }

    /**
     * No-op hook handler for missing hooks.
     */
    private static class NoOpHookHandler extends HookHandler {
        @Override
        public HookType getType() {
            return HookType.NOOP;
        }
    }
}
