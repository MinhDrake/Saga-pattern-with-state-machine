package com.learning.saga.application.saga;

import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.saga.SagaStep;
import com.learning.saga.domain.model.type.StepAction;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Factory for creating SagaContext from commands.
 * 
 * WHY A FACTORY:
 * ==============
 * 1. Encapsulates complex creation logic
 * 2. Generates IDs
 * 3. Creates appropriate saga steps based on order
 * 4. Testable in isolation
 * 
 * STEP CREATION:
 * ==============
 * Based on the order, we create a sequence of steps:
 * 1. RESERVE_INVENTORY - For each item
 * 2. CHARGE_PAYMENT - Once for total amount
 * 3. CREATE_SHIPMENT - Once for delivery
 * 4. SEND_NOTIFICATION - Confirm to customer
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SagaContextFactory {

    // Simple ID generator (in production, use distributed ID service)
    private final AtomicLong idGenerator = new AtomicLong(System.currentTimeMillis());

    private final StepFactory stepFactory;

    /**
     * Create a SagaContext from a start command.
     */
    public SagaContext create(StartSagaCommand command) {
        long orderId = idGenerator.incrementAndGet();
        
        log.info("[Factory] Creating saga context for order: {}, id: {}", 
            command.getOrderNo(), orderId);

        // Create the context
        SagaContext context = new SagaContext()
            .setOrderId(orderId)
            .setOrderNo(command.getOrderNo())
            .setCustomerId(command.getCustomerId())
            .setMetadata(command.getMetadata() != null ? command.getMetadata() : new java.util.HashMap<>());

        // Create saga steps based on order
        List<SagaStep> steps = createSteps(orderId, command);
        context.setSteps(steps);

        log.info("[Factory] Created {} steps for order: {}", steps.size(), orderId);
        return context;
    }

    /**
     * Create the sequence of saga steps for an order.
     */
    private List<SagaStep> createSteps(long orderId, StartSagaCommand command) {
        List<SagaStep> steps = new ArrayList<>();
        int index = 0;

        // Step 1: Reserve inventory for each item
        for (var item : command.getItems()) {
            steps.add(stepFactory.createInventoryStep(
                orderId, 
                index++, 
                StepAction.RESERVE_INVENTORY,
                item
            ));
        }

        // Step 2: Charge payment
        steps.add(stepFactory.createPaymentStep(
            orderId,
            index++,
            StepAction.CHARGE_PAYMENT,
            command.getPayment()
        ));

        // Step 3: Create shipment
        steps.add(stepFactory.createShippingStep(
            orderId,
            index++,
            StepAction.CREATE_SHIPMENT,
            command.getShipping()
        ));

        // Step 4: Send confirmation notification
        steps.add(stepFactory.createNotificationStep(
            orderId,
            index++,
            StepAction.SEND_NOTIFICATION,
            command.getCustomerId()
        ));

        return steps;
    }
}
