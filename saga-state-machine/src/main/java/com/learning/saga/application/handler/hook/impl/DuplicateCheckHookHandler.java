package com.learning.saga.application.handler.hook.impl;

import com.learning.saga.application.handler.hook.HookChain;
import com.learning.saga.application.handler.hook.HookHandler;
import com.learning.saga.application.handler.hook.HookResult;
import com.learning.saga.application.handler.hook.HookType;
import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.repository.SagaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Hook to prevent duplicate order processing.
 * 
 * WHY THIS HOOK:
 * ==============
 * In distributed systems, the same request might arrive multiple times:
 * - Network retries
 * - Client retries
 * - Load balancer failover
 * 
 * Without duplicate check, we might:
 * - Charge customer twice
 * - Reserve inventory twice
 * - Create duplicate orders
 * 
 * HOW IT WORKS:
 * =============
 * 1. Check if orderNo already exists in database
 * 2. If exists, reject as duplicate
 * 3. If not, continue chain and let saga be created
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DuplicateCheckHookHandler extends HookHandler {

    private final SagaRepository sagaRepository;

    @Override
    public HookResult doBefore(SagaContext context, HookChain chain) {
        log.debug("[DuplicateCheck] Checking orderNo: {}", context.getOrderNo());

        // Check if order already exists
        if (sagaRepository.existsByOrderNo(context.getOrderNo())) {
            log.warn("[DuplicateCheck] Duplicate order detected: {}", context.getOrderNo());
            return HookResult.duplicate("Order already exists: " + context.getOrderNo());
        }

        log.debug("[DuplicateCheck] Order is new, continuing");
        
        // Continue to next hook in chain
        return chain.executeBefore(context);
    }

    @Override
    public HookType getType() {
        return HookType.DUPLICATE_CHECK;
    }
}
