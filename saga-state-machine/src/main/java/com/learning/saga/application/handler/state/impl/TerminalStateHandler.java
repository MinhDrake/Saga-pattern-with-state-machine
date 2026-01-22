package com.learning.saga.application.handler.state.impl;

import com.learning.saga.application.handler.hook.HookChain;
import com.learning.saga.application.handler.hook.HookContainer;
import com.learning.saga.application.handler.state.StateHandler;
import com.learning.saga.application.handler.state.StateService;
import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.type.OrderStatus;
import com.learning.saga.domain.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * TerminalStateHandler handles all terminal (final) states.
 * 
 * TERMINAL STATES:
 * ================
 * - SUCCESS: All steps completed successfully
 * - FAILED: First step failed, nothing to compensate
 * - REVERTED: All compensations completed successfully
 * - REVERT_FAILED: Compensation failed, needs manual intervention
 * - SYSTEM_ERROR: Unexpected system error
 * 
 * WHY ONE HANDLER FOR ALL TERMINAL STATES:
 * ========================================
 * All terminal states share similar logic:
 * 1. Run "after" hooks (notifications, logging, cleanup)
 * 2. Mark saga as complete
 * 3. Return the final context
 * 
 * The difference is in WHAT the hooks do based on the status.
 * For example, SUCCESS sends confirmation email, FAILED sends apology.
 * 
 * AFTER HOOKS:
 * ============
 * These run AFTER the saga completes (success or failure):
 * - Send notification to customer
 * - Update external systems
 * - Log for analytics
 * - Cleanup temporary data
 */
@Slf4j
@RequiredArgsConstructor
@StateService(states = {
    OrderStatus.SUCCESS,
    OrderStatus.FAILED,
    OrderStatus.REVERTED,
    OrderStatus.REVERT_FAILED,
    OrderStatus.SYSTEM_ERROR,
    OrderStatus.MANUAL_REVIEW
})
public class TerminalStateHandler implements StateHandler {

    private final SagaRepository sagaRepository;
    private final HookContainer hookContainer;

    @Override
    public SagaContext process(SagaContext context) {
        log.info("[Terminal] Processing terminal state {} for order: {}", 
            context.getStatus(), context.getOrderId());

        // Step 1: Run "after" hooks
        HookChain hookChain = hookContainer.createChain(context);
        var hookResult = hookChain.executeAfter(context);
        
        if (!hookResult.isSuccess()) {
            // After hooks failed - log but don't change status
            // The saga is already complete, hooks are "best effort"
            log.warn("[Terminal] After hooks failed for order {}: {}", 
                context.getOrderId(), hookResult.getErrorMessage());
        }

        // Step 2: Log final state for audit
        logFinalState(context);

        // Step 3: Return final context
        // No delegation - this is the end of the line
        return context;
    }

    /**
     * Log the final state for audit and debugging.
     */
    private void logFinalState(SagaContext context) {
        var duration = java.time.Duration.between(
            context.getCreatedAt(), 
            context.getUpdatedAt()
        );
        
        log.info("[Terminal] Saga completed: order={}, status={}, duration={}ms, steps={}",
            context.getOrderId(),
            context.getStatus(),
            duration.toMillis(),
            context.getProcessedStepIds().size()
        );

        // Log differently based on status
        switch (context.getStatus()) {
            case SUCCESS -> log.info("[Terminal] Order {} completed successfully", 
                context.getOrderId());
            
            case FAILED -> log.warn("[Terminal] Order {} failed: {}", 
                context.getOrderId(), 
                context.getLastResult() != null ? context.getLastResult().getErrorMessage() : "unknown");
            
            case REVERTED -> log.info("[Terminal] Order {} reverted successfully", 
                context.getOrderId());
            
            case REVERT_FAILED -> log.error("[Terminal] CRITICAL: Order {} revert failed, needs manual intervention", 
                context.getOrderId());
            
            case SYSTEM_ERROR -> log.error("[Terminal] Order {} ended with system error", 
                context.getOrderId());
            
            case MANUAL_REVIEW -> log.warn("[Terminal] Order {} needs manual review", 
                context.getOrderId());
            
            default -> log.info("[Terminal] Order {} ended with status {}", 
                context.getOrderId(), context.getStatus());
        }
    }
}
