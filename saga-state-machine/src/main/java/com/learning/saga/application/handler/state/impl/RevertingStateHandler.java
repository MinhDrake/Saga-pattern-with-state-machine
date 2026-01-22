package com.learning.saga.application.handler.state.impl;

import com.learning.saga.application.handler.state.StateHandler;
import com.learning.saga.application.handler.state.StateHandlerContainer;
import com.learning.saga.application.handler.state.StateService;
import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.saga.SagaStep;
import com.learning.saga.domain.model.type.OrderStatus;
import com.learning.saga.domain.model.type.StepStatus;
import com.learning.saga.domain.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * RevertingStateHandler executes compensation steps in reverse order.
 * 
 * COMPENSATION LOGIC:
 * ===================
 * When a saga fails mid-way, we need to "undo" the steps that succeeded.
 * This handler:
 * 1. Finds steps that need compensation (SUCCEEDED steps)
 * 2. Executes compensation in REVERSE order (last succeeded → first succeeded)
 * 3. Tracks compensation status
 * 
 * WHY REVERSE ORDER:
 * ==================
 * Consider: Reserve Inventory → Charge Payment → Create Shipment
 * If Shipment fails after Payment succeeded:
 * - First undo Payment (refund)
 * - Then undo Inventory (release)
 * 
 * This ensures consistency - we undo in opposite order of doing.
 * 
 * COMPENSATION STEP TYPES:
 * ========================
 * Each action has a corresponding compensation:
 * - RESERVE_INVENTORY → RELEASE_INVENTORY
 * - CHARGE_PAYMENT → REFUND_PAYMENT
 * - CREATE_SHIPMENT → CANCEL_SHIPMENT
 * 
 * WHAT IF COMPENSATION FAILS:
 * ===========================
 * This is the worst case - we have partial state.
 * Options:
 * 1. Retry compensation (with limits)
 * 2. Mark as REVERT_FAILED for manual intervention
 * 3. Alert operations team
 */
@Slf4j
@RequiredArgsConstructor
@StateService(states = {OrderStatus.REVERTING, OrderStatus.REVERTING_PENDING})
public class RevertingStateHandler implements StateHandler {

    private final SagaRepository sagaRepository;
    private final StateHandlerContainer stateContainer;

    @Override
    public SagaContext process(SagaContext context) {
        log.info("[Reverting] Starting compensation for order: {}", context.getOrderId());

        // Step 1: Build compensation steps if not already done
        if (context.getCompensationSteps().isEmpty()) {
            List<SagaStep> forwardStepsToCompensate = context.getStepsNeedingCompensation();
            
            if (forwardStepsToCompensate.isEmpty()) {
                log.info("[Reverting] No steps need compensation");
                return handleCompensationComplete(context);
            }
            
            log.info("[Reverting] Building {} compensation steps", forwardStepsToCompensate.size());
            
            // For now, we execute compensation on the same forward steps
            // In a more sophisticated implementation, you'd create dedicated compensation steps
            context.setCompensationSteps(forwardStepsToCompensate);
        }

        // Step 2: Process compensation steps one by one
        while (context.hasMoreCompensationSteps()) {
            SagaStep step = context.getNextCompensationStep();
            log.info("[Reverting] Compensating step: {}", step.getStepId());
            
            var result = executeCompensation(step);
            
            // Handle compensation result
            switch (result.getStatus()) {
                case SUCCEEDED -> {
                    log.info("[Reverting] Compensation succeeded for {}", step.getStepId());
                    // Continue to next step (loop continues)
                }
                
                case PENDING -> {
                    log.info("[Reverting] Compensation PENDING for {}", step.getStepId());
                    context.setStatus(OrderStatus.REVERTING_PENDING);
                    context.setLastResult(result);
                    sagaRepository.updateStatus(context);
                    return context; // Wait for callback
                }
                
                case FAILED, REJECTED -> {
                    log.error("[Reverting] Compensation FAILED for {}: {}", 
                        step.getStepId(), result.getErrorMessage());
                    return handleCompensationFailed(context, step, result);
                }
                
                case UNKNOWN -> {
                    log.warn("[Reverting] Compensation UNKNOWN for {}", step.getStepId());
                    context.setStatus(OrderStatus.REVERTING_PENDING);
                    sagaRepository.updateStatus(context);
                    return context; // Will be picked up by recovery
                }
                
                default -> {
                    log.error("[Reverting] Unexpected status: {}", result.getStatus());
                }
            }
        }

        // Step 3: All compensations completed
        return handleCompensationComplete(context);
    }

    /**
     * Execute compensation for a single step.
     * 
     * WHY SEPARATE METHOD:
     * - Allows adding retry logic
     * - Easier to test
     * - Can add metrics/logging
     */
    private com.learning.saga.domain.model.saga.StepResult executeCompensation(SagaStep step) {
        // The step knows how to compensate itself
        // (via its compensation action type)
        return step.execute();
    }

    /**
     * Handle successful completion of all compensations.
     */
    private SagaContext handleCompensationComplete(SagaContext context) {
        log.info("[Reverting] All compensations complete, transitioning to REVERTED");
        context.setStatus(OrderStatus.REVERTED);
        sagaRepository.updateStatus(context);
        
        // Delegate to terminal handler for cleanup
        return stateContainer.getHandler(OrderStatus.REVERTED).process(context);
    }

    /**
     * Handle compensation failure.
     * 
     * This is a critical situation - we have partial state.
     * Must be handled carefully, often with manual intervention.
     */
    private SagaContext handleCompensationFailed(
            SagaContext context, 
            SagaStep failedStep,
            com.learning.saga.domain.model.saga.StepResult result) {
        
        log.error("[Reverting] CRITICAL: Compensation failed for step {}", failedStep.getStepId());
        log.error("[Reverting] Order {} requires manual intervention", context.getOrderId());
        
        context.setStatus(OrderStatus.REVERT_FAILED);
        context.setLastResult(result);
        sagaRepository.updateStatus(context);
        
        // TODO: Alert operations team
        // TODO: Create support ticket
        
        return context;
    }
}
