package com.learning.saga.application.handler.state;

import com.learning.saga.domain.exception.SagaException;
import com.learning.saga.domain.model.type.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Default implementation of StateHandlerContainer using BeanPostProcessor.
 * 
 * WHY BEANPOSTPROCESSOR:
 * ======================
 * Spring calls postProcessAfterInitialization() for EVERY bean created.
 * We use this hook to:
 * 1. Check if bean has @StateService annotation
 * 2. If yes, register it for the declared states
 * 
 * This achieves AUTO-REGISTRATION without any manual setup.
 * 
 * HOW IT WORKS:
 * =============
 * 1. Spring creates ProcessingStateHandler bean
 * 2. Spring calls our postProcessAfterInitialization()
 * 3. We check: does ProcessingStateHandler have @StateService?
 * 4. Yes! It declares states = {PROCESSING, PENDING}
 * 5. We register: PROCESSING -> ProcessingStateHandler
 *                 PENDING -> ProcessingStateHandler
 * 6. Later, getHandler(PROCESSING) returns ProcessingStateHandler
 * 
 * WHY AopUtils.getTargetClass():
 * ==============================
 * Spring may wrap beans in proxies (for @Transactional, AOP, etc.)
 * AopUtils.getTargetClass() gets the REAL class, not the proxy,
 * so we can read the annotation correctly.
 */
@Slf4j
@Component
public class DefaultStateHandlerContainer implements BeanPostProcessor, StateHandlerContainer {

    /**
     * Map of OrderStatus -> StateHandler.
     * Populated during Spring initialization.
     */
    private final Map<OrderStatus, StateHandler> handlers = new HashMap<>();

    /**
     * Called by Spring for every bean after initialization.
     * 
     * This is where the magic happens - we detect @StateService
     * and register the handler automatically.
     */
    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        // Get the real class (not proxy)
        var targetClass = AopUtils.getTargetClass(bean);
        
        // Check if this bean has @StateService
        if (targetClass.isAnnotationPresent(StateService.class)) {
            var annotation = targetClass.getAnnotation(StateService.class);
            
            // Register for each declared state
            for (var status : annotation.states()) {
                log.info("[StateContainer] Registering {} -> {}", status, beanName);
                handlers.put(status, (StateHandler) bean);
            }
        }
        
        return bean; // Always return the bean (required by interface)
    }

    /**
     * Get handler for a specific status.
     * 
     * @throws SagaException if no handler registered for this status
     */
    @Override
    public StateHandler getHandler(OrderStatus status) {
        return Optional.ofNullable(handlers.get(status))
            .orElseThrow(() -> SagaException.stateHandlerNotFound(status.name()));
    }

    /**
     * Check if a handler exists for the given status.
     * Useful for validation without throwing exceptions.
     */
    public boolean hasHandler(OrderStatus status) {
        return handlers.containsKey(status);
    }

    /**
     * Get all registered handlers (for debugging/monitoring).
     */
    public Map<OrderStatus, StateHandler> getAllHandlers() {
        return Map.copyOf(handlers);
    }
}
