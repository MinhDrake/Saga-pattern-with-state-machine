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

/**
 * ResumingStateHandler handles saga resumption after system restart or callback.
 * 
 * WHEN RESUMING HAPPENS:
 * ======================
 * 1. System crash while saga was PROCESSING/REVERTING
 * 2. Callback received for PENDING saga
 * 3. Recovery job picks up stuck saga
 * 
 * RESUMPTION LOGIC:
 * =================
 * 1. Query the current step's status from external system
 * 2. If step completed while we were down, update and continue
 * 3. If step still pending, re-execute or wait
 * 4. Delegate to appropriate handler based on result
 * 
 * WHY QUERY BEFORE CONTINUING:
 * ============================
 * Consider: We submitted payment, then crashed before getting response.
 * When we restart:
 * - Payment might have succeeded → don't charge again!
 * - Payment might have failed → retry or revert
 * - Payment might be pending → wait more
 * 
 * We MUST query the external system to know the truth.
 * 
 * IDEMPOTENCY IS CRITICAL:
 * ========================
 * All saga steps MUST be idempotent (safe to retry).
 * If we're not sure about a step's status, we might re-execute it.
 * The step must handle this gracefully (e.g., return COMPLETED).
 */
@Slf4j
@RequiredArgsConstructor
@StateService(states = {OrderStatus.RESUMING, OrderStatus.RESUMING_REVERTING})
public class ResumingStateHandler implements StateHandler {

    private final SagaRepository sagaRepository;
    private final StateHandlerContainer stateContainer;

    @Override
    public SagaContext process(SagaContext context) {
        log.info("[Resuming] Resuming saga for order: {}, status: {}", 
            context.getOrderId(), context.getStatus());

        // Step 1: Get the step that was in progress when we stopped
        SagaStep currentStep = context.getCurrentStep();
        
        if (currentStep == null) {
            log.warn("[Resuming] No current step found, checking if complete");
            return handleNoCurrentStep(context);
        }

        log.info("[Resuming] Querying status of step: {}", currentStep.getStepId());

        // Step 2: Query the current status from external system
        var queryResult = currentStep.query();

        // Step 3: Handle based on query result
        return switch (queryResult.getStatus()) {
            case SUCCEEDED -> {
                log.info("[Resuming] Step {} completed while we were down", currentStep.getStepId());
                currentStep.updateStatus(queryResult);
                yield continueProcessing(context);
            }
            
            case FAILED, REJECTED -> {
                log.info("[Resuming] Step {} failed while we were down", currentStep.getStepId());
                currentStep.updateStatus(queryResult);
                context.setLastResult(queryResult);
                yield handleStepFailure(context);
            }
            
            case PENDING -> {
                log.info("[Resuming] Step {} still pending", currentStep.getStepId());
                // Still waiting - stay in PENDING state
                OrderStatus pendingStatus = context.isReverting() 
                    ? OrderStatus.REVERTING_PENDING 
                    : OrderStatus.PENDING;
                context.setStatus(pendingStatus);
                sagaRepository.updateStatus(context);
                yield context;
            }
            
            case UNKNOWN -> {
                log.warn("[Resuming] Step {} status unknown, will retry", currentStep.getStepId());
                // Can't determine status - retry the step
                yield retryStep(context, currentStep);
            }
            
            default -> {
                log.error("[Resuming] Unexpected status: {}", queryResult.getStatus());
                context.setStatus(OrderStatus.SYSTEM_ERROR);
                sagaRepository.updateStatus(context);
                yield context;
            }
        };
    }

    /**
     * Continue processing after successful step resume.
     */
    private SagaContext continueProcessing(SagaContext context) {
        if (context.isLastStep()) {
            // Was the last step - saga is complete
            OrderStatus finalStatus = context.isReverting() 
                ? OrderStatus.REVERTED 
                : OrderStatus.SUCCESS;
            context.setStatus(finalStatus);
            sagaRepository.updateStatus(context);
            return stateContainer.getHandler(finalStatus).process(context);
        } else {
            // More steps - continue with appropriate handler
            OrderStatus continueStatus = context.isReverting() 
                ? OrderStatus.REVERTING 
                : OrderStatus.PROCESSING;
            context.setStatus(continueStatus);
            sagaRepository.updateStatus(context);
            return stateContainer.getHandler(continueStatus).process(context);
        }
    }

    /**
     * Handle step failure during resume.
     */
    private SagaContext handleStepFailure(SagaContext context) {
        if (context.isReverting()) {
            // Compensation step failed
            context.setStatus(OrderStatus.REVERT_FAILED);
        } else {
            // Forward step failed - evaluate what to do
            OrderStatus nextStatus = context.evaluateFailedStep();
            context.setStatus(nextStatus);
        }
        
        sagaRepository.updateStatus(context);
        return stateContainer.getHandler(context.getStatus()).process(context);
    }

    /**
     * Retry a step when status is unknown.
     */
    private SagaContext retryStep(SagaContext context, SagaStep step) {
        // Re-execute the step (must be idempotent!)
        log.info("[Resuming] Re-executing step: {}", step.getStepId());
        var result = step.execute();
        
        // Handle result same as normal processing
        if (result.isSuccess()) {
            return continueProcessing(context);
        } else if (result.shouldWait()) {
            OrderStatus pendingStatus = context.isReverting() 
                ? OrderStatus.REVERTING_PENDING 
                : OrderStatus.PENDING;
            context.setStatus(pendingStatus);
            sagaRepository.updateStatus(context);
            return context;
        } else {
            context.setLastResult(result);
            return handleStepFailure(context);
        }
    }

    /**
     * Handle case when there's no current step.
     */
    private SagaContext handleNoCurrentStep(SagaContext context) {
        // This might mean all steps are done
        if (context.isLastStep()) {
            OrderStatus finalStatus = context.isReverting() 
                ? OrderStatus.REVERTED 
                : OrderStatus.SUCCESS;
            context.setStatus(finalStatus);
            sagaRepository.updateStatus(context);
            return stateContainer.getHandler(finalStatus).process(context);
        }
        
        // Shouldn't happen - log error and mark for review
        log.error("[Resuming] No current step and not complete - marking for review");
        context.setStatus(OrderStatus.MANUAL_REVIEW);
        sagaRepository.updateStatus(context);
        return context;
    }
}
