package com.learning.saga.application.handler.state;

import com.learning.saga.domain.model.saga.SagaContext;

/**
 * StateHandler defines the contract for processing saga in a specific state.
 * 
 * WHY STATE HANDLER PATTERN:
 * ==========================
 * 1. SINGLE RESPONSIBILITY: Each handler manages ONE state (or group of related states)
 * 2. OPEN/CLOSED: Add new states without modifying existing handlers
 * 3. TESTABILITY: Each handler can be tested in isolation
 * 
 * KEY PRINCIPLE:
 * ==============
 * *** ONLY StateHandler can modify SagaContext ***
 * 
 * This principle ensures:
 * - State changes are predictable and traceable
 * - No "rogue" modifications from random places
 * - Clear audit trail of who changed what
 * 
 * IMPLEMENTATION PATTERN:
 * =======================
 * 1. Annotate with @StateService(states = {...}) to register for states
 * 2. Implement process() with state-specific logic
 * 3. Call container.getHandler(nextState).process(context) to delegate
 * 
 * EXAMPLE:
 * ========
 * {@code
 * @StateService(states = {OrderStatus.PROCESSING})
 * public class ProcessingStateHandler implements StateHandler {
 *     public SagaContext process(SagaContext context) {
 *         var step = context.getNextStep();
 *         var result = step.execute();
 *         // Handle result, transition state, delegate to next handler
 *     }
 * }
 * }
 */
public interface StateHandler {

    /**
     * Process the saga context for the current state.
     * 
     * CONTRACT:
     * - May modify context.status (that's the whole point)
     * - Should persist state changes via repository
     * - Should delegate to next handler if not terminal
     * - Should NOT throw exceptions (handle errors via status transitions)
     * 
     * @param context The saga context to process
     * @return The processed context (possibly with new status)
     */
    SagaContext process(SagaContext context);
}
