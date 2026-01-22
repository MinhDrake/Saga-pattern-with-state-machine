package com.learning.saga.application.handler.state.impl;

import com.learning.saga.application.handler.hook.HookChain;
import com.learning.saga.application.handler.hook.HookContainer;
import com.learning.saga.application.handler.hook.HookResult;
import com.learning.saga.application.handler.state.StateHandler;
import com.learning.saga.application.handler.state.StateHandlerContainer;
import com.learning.saga.application.handler.state.StateService;
import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.type.OrderStatus;
import com.learning.saga.domain.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * InitStateHandler processes sagas in INIT state.
 * 
 * RESPONSIBILITY:
 * ===============
 * 1. Run "before" hooks (validation, deduplication, etc.)
 * 2. If hooks pass, transition to PROCESSING
 * 3. If hooks fail, transition to appropriate error state
 * 
 * THIS IS THE ENTRY POINT:
 * ========================
 * Every saga starts here. The Init handler decides whether
 * the saga can proceed or should be rejected early.
 * 
 * HOOK INTEGRATION:
 * =================
 * Before processing, we run hooks like:
 * - Duplicate check (same order already processing?)
 * - Validation (is order valid?)
 * - Authorization (is user allowed?)
 * 
 * If any hook fails, we reject without wasting resources.
 */
@Slf4j
@RequiredArgsConstructor
@StateService(states = {OrderStatus.INIT})
public class InitStateHandler implements StateHandler {

    private final SagaRepository sagaRepository;
    private final StateHandlerContainer stateContainer;
    private final HookContainer hookContainer;

    @Override
    public SagaContext process(SagaContext context) {
        log.info("[Init] Processing saga for order: {}", context.getOrderId());

        // Step 1: Run "before" hooks
        HookChain hookChain = hookContainer.createChain(context);
        HookResult hookResult = hookChain.executeBefore(context);

        // Step 2: Check hook result
        if (!hookResult.isSuccess()) {
            log.warn("[Init] Hooks failed for order {}: {}", 
                context.getOrderId(), hookResult.getErrorMessage());
            
            return handleHookFailure(context, hookResult);
        }

        // Step 3: Hooks passed - transition to PROCESSING
        log.info("[Init] Hooks passed, transitioning to PROCESSING");
        context.setStatus(OrderStatus.PROCESSING);
        
        // Step 4: Persist the state change
        boolean saved = sagaRepository.updateStatus(context);
        if (!saved) {
            log.error("[Init] Failed to save status for order {}", context.getOrderId());
            context.setStatus(OrderStatus.SYSTEM_ERROR);
            return context;
        }

        // Step 5: Delegate to PROCESSING handler
        return stateContainer.getHandler(OrderStatus.PROCESSING).process(context);
    }

    /**
     * Handle hook failure by setting appropriate status.
     */
    private SagaContext handleHookFailure(SagaContext context, HookResult result) {
        OrderStatus failureStatus = switch (result.getFailureType()) {
            case DUPLICATE -> OrderStatus.FAILED; // Duplicate, no need to process
            case VALIDATION -> OrderStatus.FAILED; // Invalid input
            case AUTHORIZATION -> OrderStatus.FAILED; // Not authorized
            default -> OrderStatus.SYSTEM_ERROR; // Unexpected hook failure
        };

        context.setStatus(failureStatus);
        context.setLastResult(result.toStepResult());
        sagaRepository.updateStatus(context);
        
        return context;
    }
}
