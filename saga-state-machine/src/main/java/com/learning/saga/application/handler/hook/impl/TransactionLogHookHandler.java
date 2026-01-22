package com.learning.saga.application.handler.hook.impl;

import com.learning.saga.application.handler.hook.HookChain;
import com.learning.saga.application.handler.hook.HookHandler;
import com.learning.saga.application.handler.hook.HookResult;
import com.learning.saga.application.handler.hook.HookType;
import com.learning.saga.domain.model.saga.SagaContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Hook to log transaction details for audit and analytics.
 * 
 * WHY THIS HOOK:
 * ==============
 * Transaction logs are critical for:
 * 1. Audit trail (compliance, disputes)
 * 2. Analytics (business insights)
 * 3. Debugging (what happened?)
 * 4. Monitoring (alert on patterns)
 * 
 * WHAT WE LOG:
 * ============
 * - Order ID and status
 * - Duration (latency)
 * - Steps processed
 * - Error details (if failed)
 */
@Slf4j
@Component
public class TransactionLogHookHandler extends HookHandler {

    @Override
    public HookResult doAfter(SagaContext context, HookChain chain) {
        // Calculate duration
        var duration = Duration.between(context.getCreatedAt(), context.getUpdatedAt());

        // Build log entry
        var logEntry = TransactionLog.builder()
            .orderId(context.getOrderId())
            .orderNo(context.getOrderNo())
            .customerId(context.getCustomerId())
            .status(context.getStatus().name())
            .durationMs(duration.toMillis())
            .stepsProcessed(context.getProcessedStepIds().size())
            .errorCode(context.getLastResult() != null && context.getLastResult().getErrorCode() != null
                ? context.getLastResult().getErrorCode().getCode() : null)
            .errorMessage(context.getLastResult() != null 
                ? context.getLastResult().getErrorMessage() : null)
            .build();

        // Log as structured JSON (in real system, send to analytics)
        log.info("[TransactionLog] {}", logEntry);

        // Continue to next hook
        return chain.executeAfter(context);
    }

    @Override
    public HookType getType() {
        return HookType.TRANSACTION_LOG;
    }

    /**
     * Structured transaction log entry.
     */
    @lombok.Builder
    @lombok.Data
    private static class TransactionLog {
        private long orderId;
        private String orderNo;
        private long customerId;
        private String status;
        private long durationMs;
        private int stepsProcessed;
        private Integer errorCode;
        private String errorMessage;
    }
}
