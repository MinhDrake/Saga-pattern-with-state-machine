package com.learning.saga.application.saga;

import com.learning.saga.application.handler.state.StateHandlerContainer;
import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.type.OrderStatus;
import com.learning.saga.domain.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Default implementation of SagaEngine.
 * 
 * ORCHESTRATION PATTERN:
 * ======================
 * This engine uses the ORCHESTRATION pattern for sagas:
 * - A central coordinator (this engine) manages the flow
 * - Steps don't know about each other
 * - Engine decides what to do next based on step results
 * 
 * Alternative: CHOREOGRAPHY pattern
 * - Each step triggers the next via events
 * - No central coordinator
 * - More decoupled but harder to track flow
 * 
 * See docs/PATTERN_COMPARISON.md for detailed comparison.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefaultSagaEngine implements SagaEngine {

    private final SagaRepository sagaRepository;
    private final StateHandlerContainer stateContainer;
    private final SagaContextFactory contextFactory;

    @Override
    public SagaContext start(StartSagaCommand command) {
        log.info("[SagaEngine] Starting saga for order: {}", command.getOrderNo());

        // Step 1: Create and initialize the saga context
        SagaContext context = contextFactory.create(command);
        context.setStatus(OrderStatus.INIT);
        context.initSaga();

        // Step 2: Persist initial state
        boolean saved = sagaRepository.create(context);
        if (!saved) {
            log.error("[SagaEngine] Failed to persist saga for order: {}", command.getOrderNo());
            context.setStatus(OrderStatus.SYSTEM_ERROR);
            return context;
        }

        log.info("[SagaEngine] Saga created with ID: {}", context.getOrderId());

        // Step 3: Delegate to INIT state handler
        // The state machine takes over from here
        return stateContainer.getHandler(OrderStatus.INIT).process(context);
    }

    @Override
    public SagaContext resume(ResumeSagaCommand command) {
        log.info("[SagaEngine] Resuming saga for order: {}, source: {}", 
            command.getOrderId(), command.getSource());

        // Step 1: Load the saga context
        Optional<SagaContext> optContext = sagaRepository.findById(command.getOrderId());
        if (optContext.isEmpty()) {
            log.error("[SagaEngine] Saga not found for order: {}", command.getOrderId());
            return null;
        }

        SagaContext context = optContext.get();
        log.info("[SagaEngine] Loaded saga with status: {}", context.getStatus());

        // Step 2: Check if saga can be resumed
        if (context.isTerminal()) {
            log.warn("[SagaEngine] Cannot resume terminal saga: {}", context.getStatus());
            return context;
        }

        // Step 3: Apply callback result if provided
        if (command.getStepId() != null && command.getCallbackResult() != null) {
            context.findStep(command.getStepId())
                .ifPresent(step -> step.updateStatus(command.getCallbackResult()));
        }

        // Step 4: Transition to resume status
        OrderStatus resumeStatus = context.getStatus().toResumeStatus();
        context.setStatus(resumeStatus);
        sagaRepository.updateStatus(context);

        // Step 5: Delegate to appropriate handler
        return stateContainer.getHandler(resumeStatus).process(context);
    }

    @Override
    public Optional<SagaContext> query(long orderId) {
        log.debug("[SagaEngine] Querying saga for order: {}", orderId);
        return sagaRepository.findById(orderId);
    }

    @Override
    public boolean exists(String orderNo) {
        return sagaRepository.existsByOrderNo(orderNo);
    }
}
