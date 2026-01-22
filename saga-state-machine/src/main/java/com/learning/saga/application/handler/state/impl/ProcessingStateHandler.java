package com.learning.saga.application.handler.state.impl;

import com.learning.saga.application.handler.state.StateHandler;
import com.learning.saga.application.handler.state.StateHandlerContainer;
import com.learning.saga.application.handler.state.StateService;
import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.saga.StepResult;
import com.learning.saga.domain.model.type.OrderStatus;
import com.learning.saga.domain.model.type.StepStatus;
import com.learning.saga.domain.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * ProcessingStateHandler executes saga steps in forward direction.
 * 
 * THIS IS THE CORE HANDLER:
 * =========================
 * This handler does the actual work of executing saga steps.
 * It handles both PROCESSING and PENDING states because they
 * share similar logic (continue executing steps).
 * 
 * PROCESSING LOOP:
 * ================
 * 1. Get next step from context
 * 2. Execute the step
 * 3. Based on result:
 *    - SUCCEEDED: Continue to next step or SUCCESS
 *    - FAILED: Evaluate and possibly REVERT
 *    - PENDING: Wait for callback
 *    - UNKNOWN: Mark as PENDING
 * 
 * WHY SWITCH EXPRESSION:
 * ======================
 * Java 17 switch expressions provide:
 * - Exhaustive checking (compiler ensures all cases handled)
 * - Cleaner syntax than if-else chains
 * - Each case yields a value (the processed context)
 * 
 * DELEGATION PATTERN:
 * ===================
 * Notice how we call container.getHandler(newStatus).process(context)
 * This creates a chain: PROCESSING -> SUCCESS (or REVERTING)
 * Each handler focuses on its own logic, then delegates.
 */
@Slf4j
@RequiredArgsConstructor
@StateService(states = {OrderStatus.PROCESSING, OrderStatus.PENDING})
public class ProcessingStateHandler implements StateHandler {

    private final SagaRepository sagaRepository;
    private final StateHandlerContainer stateContainer;

    @Override
    public SagaContext process(SagaContext context) {
        log.info("[Processing] Executing next step for order: {}", context.getOrderId());

        // Step 1: Get the next step to execute
        var step = context.getNextStep();
        log.info("[Processing] Executing step: {}", step.getStepId());

        // Step 2: Execute the step
        var result = step.execute();

        // Step 3: Handle the result using switch expression
        return switch (result.getStatus()) {
            
            case COMPLETED -> {
                // Step was already completed (e.g., during recovery)
                // This can happen if we're resuming and step finished while we were down
                log.info("[Processing] Step {} already COMPLETED, skipping", step.getStepId());
                yield context;
            }
            
            case PENDING -> {
                // Step submitted to external system, waiting for callback
                // Example: Payment submitted to gateway, waiting for webhook
                log.info("[Processing] Step {} PENDING, waiting for callback", step.getStepId());
                context.setStatus(OrderStatus.PENDING);
                context.setLastResult(result);
                sagaRepository.updateStatus(context);
                yield context; // Don't delegate, wait for callback
            }
            
            case UNKNOWN -> {
                // Couldn't determine result (e.g., timeout, network error)
                // Mark as PENDING so recovery job can query later
                log.warn("[Processing] Step {} UNKNOWN, marking PENDING", step.getStepId());
                context.setStatus(OrderStatus.PENDING);
                context.setLastResult(result);
                sagaRepository.updateStatus(context);
                yield context;
            }
            
            case SUCCEEDED -> {
                log.info("[Processing] Step {} SUCCEEDED", step.getStepId());
                context.setLastResult(result);
                
                if (context.isLastStep()) {
                    // All steps completed successfully!
                    yield handleSuccess(context);
                } else {
                    // More steps to go - continue processing
                    // Recursive call to process next step
                    yield stateContainer.getHandler(OrderStatus.PROCESSING).process(context);
                }
            }
            
            case FAILED -> {
                log.error("[Processing] Step {} FAILED: {}", 
                    step.getStepId(), result.getErrorMessage());
                yield handleFailure(context, result);
            }
            
            case REJECTED -> {
                // Business rejection (e.g., insufficient balance)
                // Different from FAILED - may need different handling
                log.warn("[Processing] Step {} REJECTED: {}", 
                    step.getStepId(), result.getErrorMessage());
                yield handleFailure(context, result);
            }
            
            case PROCESSING -> {
                // Shouldn't happen - step returned PROCESSING status
                log.error("[Processing] Step {} returned invalid PROCESSING status", 
                    step.getStepId());
                context.setStatus(OrderStatus.SYSTEM_ERROR);
                sagaRepository.updateStatus(context);
                yield context;
            }
            
            case EXECUTING, TIMEOUT, SKIPPED -> {
                // EXECUTING: Step is still in progress (treat as PENDING)
                // TIMEOUT: Step timed out (handle as failure)
                // SKIPPED: Step was skipped (continue to next)
                log.warn("[Processing] Step {} returned unexpected status: {}", 
                    step.getStepId(), result.getStatus());
                if (result.getStatus() == StepStatus.TIMEOUT) {
                    yield handleFailure(context, result);
                } else {
                    context.setStatus(OrderStatus.PENDING);
                    sagaRepository.updateStatus(context);
                    yield context;
                }
            }
            
            // Compensation-related statuses shouldn't occur during forward processing
            case NEEDS_COMPENSATION, COMPENSATING, COMPENSATED, COMPENSATION_FAILED -> {
                log.error("[Processing] Step {} returned compensation status during forward processing: {}", 
                    step.getStepId(), result.getStatus());
                context.setStatus(OrderStatus.SYSTEM_ERROR);
                sagaRepository.updateStatus(context);
                yield context;
            }
        };
    }

    /**
     * Handle successful completion of all steps.
     */
    private SagaContext handleSuccess(SagaContext context) {
        log.info("[Processing] All steps completed, transitioning to SUCCESS");
        context.setStatus(OrderStatus.SUCCESS);
        
        boolean saved = sagaRepository.updateStatus(context);
        if (!saved) {
            log.error("[Processing] Failed to save SUCCESS status");
            // Still return context - status is SUCCESS in memory
        }
        
        // Delegate to SUCCESS handler (for cleanup, notifications, etc.)
        return stateContainer.getHandler(OrderStatus.SUCCESS).process(context);
    }

    /**
     * Handle step failure - evaluate whether to revert or fail.
     * 
     * WHY evaluateFailedStep():
     * ========================
     * Not all failures should trigger compensation:
     * - First step failed? Nothing to compensate, just FAILED
     * - Later step failed? Need to compensate previous steps
     * - Some steps succeeded that can't be undone? MANUAL_REVIEW
     */
    private SagaContext handleFailure(SagaContext context, StepResult result) {
        context.setLastResult(result);
        
        // Evaluate what to do based on current state
        OrderStatus nextStatus = context.evaluateFailedStep();
        log.info("[Processing] Failure evaluated, next status: {}", nextStatus);
        
        context.setStatus(nextStatus);
        boolean saved = sagaRepository.updateStatus(context);
        
        if (saved && nextStatus == OrderStatus.REVERTING) {
            // Need to compensate - delegate to REVERTING handler
            return stateContainer.getHandler(OrderStatus.REVERTING).process(context);
        }
        
        // Terminal failure state - no further processing
        return context;
    }
}
