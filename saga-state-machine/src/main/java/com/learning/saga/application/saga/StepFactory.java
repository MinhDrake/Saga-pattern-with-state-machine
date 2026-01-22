package com.learning.saga.application.saga;

import com.learning.saga.domain.model.saga.SagaStep;
import com.learning.saga.domain.model.type.StepAction;
import org.springframework.stereotype.Component;

/**
 * Factory for creating different types of saga steps.
 * 
 * WHY A FACTORY:
 * ==============
 * Each step type may need different dependencies (API clients, etc.)
 * The factory wires these dependencies when creating steps.
 * 
 * STEP TYPES:
 * ===========
 * - InventorySagaStep: Interacts with inventory service
 * - PaymentSagaStep: Interacts with payment gateway
 * - ShippingSagaStep: Interacts with shipping service
 * - NotificationSagaStep: Sends customer notifications
 */
@Component
public class StepFactory {

    // In real implementation, these would be injected API clients
    // private final InventoryApiClient inventoryClient;
    // private final PaymentApiClient paymentClient;
    // private final ShippingApiClient shippingClient;
    // private final NotificationApiClient notificationClient;

    public SagaStep createInventoryStep(
            long orderId, 
            int index, 
            StepAction action,
            StartSagaCommand.OrderItem item) {
        
        // Return a concrete InventorySagaStep implementation
        // For learning project, we'll return a mock step
        return new MockSagaStep(orderId, index, action, "INVENTORY", item.getProductId());
    }

    public SagaStep createPaymentStep(
            long orderId,
            int index,
            StepAction action,
            StartSagaCommand.PaymentInfo payment) {
        
        return new MockSagaStep(orderId, index, action, "PAYMENT", payment.getAccountId());
    }

    public SagaStep createShippingStep(
            long orderId,
            int index,
            StepAction action,
            StartSagaCommand.ShippingInfo shipping) {
        
        return new MockSagaStep(orderId, index, action, "SHIPPING", shipping.getAddress());
    }

    public SagaStep createNotificationStep(
            long orderId,
            int index,
            StepAction action,
            long customerId) {
        
        return new MockSagaStep(orderId, index, action, "NOTIFICATION", String.valueOf(customerId));
    }
}
