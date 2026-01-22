package com.learning.saga.infrastructure.persistence;

import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.saga.StepLog;
import com.learning.saga.domain.model.type.OrderStatus;
import com.learning.saga.domain.repository.SagaRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of SagaRepository for learning/testing.
 * 
 * In production, this would be replaced with a database implementation
 * (MySQL, PostgreSQL, etc.)
 * 
 * WHY IN-MEMORY FOR LEARNING:
 * ===========================
 * 1. No database setup required
 * 2. Easy to debug (can inspect maps)
 * 3. Fast for testing
 * 4. Demonstrates the interface without infrastructure complexity
 */
@Slf4j
@Repository
public class InMemorySagaRepository implements SagaRepository {

    private final Map<Long, SagaContext> sagasByOrderId = new ConcurrentHashMap<>();
    private final Map<String, Long> orderNoToOrderId = new ConcurrentHashMap<>();
    private final Map<Long, List<StepLog>> stepsByOrderId = new ConcurrentHashMap<>();

    @Override
    public boolean create(SagaContext context) {
        log.debug("[Repository] Creating saga: {}", context.getOrderId());
        
        if (sagasByOrderId.containsKey(context.getOrderId())) {
            log.warn("[Repository] Saga already exists: {}", context.getOrderId());
            return false;
        }
        
        sagasByOrderId.put(context.getOrderId(), context);
        orderNoToOrderId.put(context.getOrderNo(), context.getOrderId());
        stepsByOrderId.put(context.getOrderId(), new ArrayList<>());
        
        return true;
    }

    @Override
    public boolean updateStatus(SagaContext context) {
        log.debug("[Repository] Updating saga {} to status {}", 
            context.getOrderId(), context.getStatus());
        
        var existing = sagasByOrderId.get(context.getOrderId());
        if (existing == null) {
            log.warn("[Repository] Saga not found: {}", context.getOrderId());
            return false;
        }
        
        // Simulate optimistic locking
        if (!existing.getUpdatedAt().equals(context.getUpdatedAt())) {
            log.warn("[Repository] Optimistic lock failed for saga: {}", context.getOrderId());
            return false;
        }
        
        sagasByOrderId.put(context.getOrderId(), context);
        return true;
    }

    @Override
    public Optional<SagaContext> findById(long orderId) {
        return Optional.ofNullable(sagasByOrderId.get(orderId));
    }

    @Override
    public Optional<SagaContext> findByOrderNo(String orderNo) {
        var orderId = orderNoToOrderId.get(orderNo);
        if (orderId == null) {
            return Optional.empty();
        }
        return findById(orderId);
    }

    @Override
    public boolean existsByOrderNo(String orderNo) {
        return orderNoToOrderId.containsKey(orderNo);
    }

    @Override
    public boolean saveSteps(List<StepLog> steps) {
        if (steps.isEmpty()) {
            return true;
        }
        
        long orderId = steps.get(0).getOrderId();
        stepsByOrderId.computeIfAbsent(orderId, k -> new ArrayList<>()).addAll(steps);
        return true;
    }

    @Override
    public List<StepLog> loadSteps(long orderId) {
        return new ArrayList<>(stepsByOrderId.getOrDefault(orderId, List.of()));
    }

    @Override
    public List<SagaContext> findStuckSagas(List<OrderStatus> statuses, int olderThanMinutes, int limit) {
        var cutoff = Instant.now().minus(olderThanMinutes, ChronoUnit.MINUTES);
        
        return sagasByOrderId.values().stream()
            .filter(s -> statuses.contains(s.getStatus()))
            .filter(s -> s.getUpdatedAt().isBefore(cutoff))
            .limit(limit)
            .collect(Collectors.toList());
    }

    /**
     * Clear all data (for testing).
     */
    public void clear() {
        sagasByOrderId.clear();
        orderNoToOrderId.clear();
        stepsByOrderId.clear();
    }

    /**
     * Get all sagas (for debugging).
     */
    public Collection<SagaContext> findAll() {
        return new ArrayList<>(sagasByOrderId.values());
    }
}
