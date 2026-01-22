package com.learning.saga.application.handler.state;

import com.learning.saga.domain.model.type.OrderStatus;
import org.springframework.stereotype.Service;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to register a StateHandler for specific OrderStatus values.
 * 
 * WHY ANNOTATION-BASED REGISTRATION:
 * ==================================
 * 1. DECLARATIVE: Handler declares what states it handles, not imperative registration
 * 2. AUTO-DISCOVERY: Spring + BeanPostProcessor automatically discovers and registers
 * 3. COMPILE-TIME SAFETY: Typos in state names cause compilation errors
 * 4. SELF-DOCUMENTING: Looking at handler, you know what states it handles
 * 
 * HOW IT WORKS:
 * =============
 * 1. @StateService includes @Service, making it a Spring bean
 * 2. StateHandlerContainer implements BeanPostProcessor
 * 3. On bean initialization, container checks for @StateService
 * 4. If found, registers handler for each state in states()
 * 
 * USAGE:
 * ======
 * {@code
 * @StateService(states = {OrderStatus.PROCESSING, OrderStatus.PENDING})
 * public class ProcessingStateHandler implements StateHandler {
 *     // This handler will be called for PROCESSING and PENDING states
 * }
 * }
 * 
 * DESIGN DECISION - MULTIPLE STATES:
 * ===================================
 * One handler CAN handle multiple states because:
 * - Related states often share similar logic (PROCESSING & PENDING)
 * - Reduces code duplication
 * - States are still explicit (not "default" handler)
 */
@Service  // Makes the annotated class a Spring bean
@Target(ElementType.TYPE)  // Can only be applied to classes
@Retention(RetentionPolicy.RUNTIME)  // Available at runtime for reflection
public @interface StateService {

    /**
     * The states this handler is responsible for.
     * 
     * At least one state must be specified.
     * Handler will be invoked when context.status matches any of these.
     */
    OrderStatus[] states();
}
