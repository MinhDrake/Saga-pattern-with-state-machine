package com.learning.saga.application.handler.hook.impl;

import com.learning.saga.application.handler.hook.HookChain;
import com.learning.saga.application.handler.hook.HookHandler;
import com.learning.saga.application.handler.hook.HookResult;
import com.learning.saga.application.handler.hook.HookType;
import com.learning.saga.domain.model.saga.SagaContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hook to validate order data before processing.
 * 
 * WHY THIS HOOK:
 * ==============
 * Validate input early to fail fast:
 * - Invalid data shouldn't start a saga
 * - Clearer error messages for users
 * - Saves resources (no partial processing)
 * 
 * VALIDATIONS:
 * ============
 * 1. Order has items
 * 2. Order ID is valid
 * 3. Customer ID is valid
 * 4. (Add more as needed)
 */
@Slf4j
@Component
public class ValidationHookHandler extends HookHandler {

    @Override
    public HookResult doBefore(SagaContext context, HookChain chain) {
        log.debug("[Validation] Validating order: {}", context.getOrderId());

        // Validation 1: Order must have an ID
        if (context.getOrderId() <= 0) {
            return HookResult.validationFailed("Invalid order ID");
        }

        // Validation 2: Customer must be specified
        if (context.getCustomerId() <= 0) {
            return HookResult.validationFailed("Invalid customer ID");
        }

        // Validation 3: Order must have steps
        if (context.getSteps() == null || context.getSteps().isEmpty()) {
            return HookResult.validationFailed("Order has no items to process");
        }

        log.debug("[Validation] Order is valid");
        
        // Continue to next hook
        return chain.executeBefore(context);
    }

    @Override
    public HookType getType() {
        return HookType.VALIDATION;
    }
}
