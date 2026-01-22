package com.learning.saga.domain.repository;

import com.learning.saga.domain.model.saga.SagaContext;
import com.learning.saga.domain.model.saga.StepLog;
import com.learning.saga.domain.model.type.OrderStatus;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for saga persistence.
 * 
 * WHY AN INTERFACE (PORT):
 * ========================
 * Following Hexagonal Architecture, the repository is a PORT:
 * - Domain defines the interface (what it needs)
 * - Infrastructure provides the implementation (how it's done)
 * 
 * This allows:
 * - Swapping database implementations (MySQL, MongoDB, etc.)
 * - Testing with in-memory implementation
 * - Clear separation of concerns
 * 
 * PERSISTENCE STRATEGY:
 * =====================
 * Sagas are persisted in two tables:
 * 1. saga_context - Main saga state (one row per saga)
 * 2. saga_step - Step states (multiple rows per saga)
 * 
 * Both tables can be partitioned by month for scalability.
 */
public interface SagaRepository {

    /**
     * Create a new saga context.
     * 
     * @param context The saga to persist
     * @return true if created successfully
     */
    boolean create(SagaContext context);

    /**
     * Update the status of a saga.
     * 
     * Uses optimistic locking to prevent concurrent updates.
     * 
     * @param context The saga with new status
     * @return true if updated successfully
     */
    boolean updateStatus(SagaContext context);

    /**
     * Find a saga by order ID.
     * 
     * @param orderId The order ID
     * @return The saga if found
     */
    Optional<SagaContext> findById(long orderId);

    /**
     * Find a saga by external order number.
     * 
     * @param orderNo The external order number
     * @return The saga if found
     */
    Optional<SagaContext> findByOrderNo(String orderNo);

    /**
     * Check if a saga exists for the given order number.
     * 
     * @param orderNo The external order number
     * @return true if exists
     */
    boolean existsByOrderNo(String orderNo);

    /**
     * Save step logs for a saga.
     * 
     * @param steps The steps to save
     * @return true if saved successfully
     */
    boolean saveSteps(List<StepLog> steps);

    /**
     * Load step logs for a saga.
     * 
     * @param orderId The order ID
     * @return List of step logs
     */
    List<StepLog> loadSteps(long orderId);

    /**
     * Find sagas that are stuck (for recovery).
     * 
     * @param statuses The statuses to look for
     * @param olderThanMinutes Minutes since last update
     * @param limit Maximum number to return
     * @return List of stuck sagas
     */
    List<SagaContext> findStuckSagas(List<OrderStatus> statuses, int olderThanMinutes, int limit);
}
