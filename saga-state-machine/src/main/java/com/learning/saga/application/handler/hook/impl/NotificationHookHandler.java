package com.learning.saga.application.handler.hook.impl;

import com.learning.saga.application.handler.hook.HookChain;
import com.learning.saga.application.handler.hook.HookHandler;
import com.learning.saga.application.handler.hook.HookResult;
import com.learning.saga.application.handler.hook.HookType;
import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.type.OrderStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hook to send notifications after saga completes.
 * 
 * WHY THIS HOOK:
 * ==============
 * Customers need to know the outcome of their order:
 * - SUCCESS: "Your order is confirmed!"
 * - FAILED: "Sorry, we couldn't process your order"
 * - REVERTED: "Your order was cancelled and refunded"
 * 
 * WHY AFTER HOOK:
 * ===============
 * Notifications are sent AFTER the saga completes because:
 * 1. We need to know the final status
 * 2. Notification failure shouldn't fail the saga
 * 3. Can include final order details
 */
@Slf4j
@Component
public class NotificationHookHandler extends HookHandler {

    @Override
    public HookResult doAfter(SagaContext context, HookChain chain) {
        log.info("[Notification] Sending notification for order: {}, status: {}", 
            context.getOrderId(), context.getStatus());

        try {
            sendNotification(context);
        } catch (Exception e) {
            // Log but don't fail - notification is best effort
            log.error("[Notification] Failed to send notification", e);
        }

        // Continue to next hook regardless of notification result
        return chain.executeAfter(context);
    }

    private void sendNotification(SagaContext context) {
        // In real implementation, this would call a notification service
        // For learning, we just log
        
        String message = switch (context.getStatus()) {
            case SUCCESS -> "🎉 Your order #%s has been confirmed!".formatted(context.getOrderNo());
            case FAILED -> "❌ Sorry, we couldn't process order #%s".formatted(context.getOrderNo());
            case REVERTED -> "↩️ Order #%s was cancelled and refunded".formatted(context.getOrderNo());
            case REVERT_FAILED -> "⚠️ There was an issue with order #%s, our team is looking into it".formatted(context.getOrderNo());
            default -> "Order #%s status: %s".formatted(context.getOrderNo(), context.getStatus());
        };

        log.info("[Notification] Would send: {}", message);
        
        // notificationService.send(context.getCustomerId(), message);
    }

    @Override
    public HookType getType() {
        return HookType.NOTIFICATION;
    }
}
