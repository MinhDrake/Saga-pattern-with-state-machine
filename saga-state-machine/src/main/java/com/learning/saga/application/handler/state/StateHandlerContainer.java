package com.learning.saga.application.handler.state;

import com.learning.saga.domain.model.type.OrderStatus;

/**
 * StateHandlerContainer is a registry that maps OrderStatus to StateHandler.
 * 
 * WHY A CONTAINER:
 * ================
 * 1. LOOKUP: Given a status, find the right handler
 * 2. DECOUPLING: Handlers don't know about each other, only the container
 * 3. DELEGATION: Handlers delegate to container for next handler
 * 
 * DELEGATION PATTERN:
 * ===================
 * When ProcessingStateHandler finishes, it needs to call the next handler:
 * 
 * {@code
 * // In ProcessingStateHandler.process():
 * context.setStatus(OrderStatus.SUCCESS);
 * return container.getHandler(OrderStatus.SUCCESS).process(context);
 * }
 * 
 * This creates a CHAIN of handlers, each delegating to the next
 * until a terminal state is reached.
 */
public interface StateHandlerContainer {

    /**
     * Get the handler for a specific status.
     * 
     * @param status The order status to find handler for
     * @return The registered handler
     * @throws com.learning.saga.domain.exception.SagaException if no handler found
     */
    StateHandler getHandler(OrderStatus status);
}
