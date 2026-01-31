package com.minhdrake.saga.domain.repository;

import com.minhdrake.saga.domain.model.saga.SagaContext;
import com.minhdrake.saga.domain.model.saga.StepLog;

import java.util.List;
import java.util.Optional;

/**
 * SagaRepository interface for persisting saga state.
 * 
 * WHY A REPOSITORY INTERFACE:
 * ===========================
 * 1. ABSTRACTION: Handlers don't care about storage details
 * 2. TESTABILITY: Easy to mock in unit tests
 * 3. FLEXIBILITY: Can switch storage (DB, Redis, etc.) without changing
 * handlers
 * 
 * PERSISTENCE STRATEGY:
 * =====================
 * The saga engine needs to persist:
 * 1. SagaContext: Overall saga state (status, timeout, metadata)
 * 2. StepLog: Individual step execution history
 * 
 * This allows recovery after failures - we can reconstruct the saga
 * state from persisted data and continue where we left off.
 * 
 * ACID CONSIDERATIONS:
 * ====================
 * - Status updates should be atomic
 * - Step logs should be append-only (never delete, for audit trail)
 * - Consider eventual consistency for non-critical reads
 */
public interface SagaRepository {

    /**
     * Save a new saga context.
     * 
     * @param context The saga context to save
     * @return true if saved successfully
     */
    boolean save(SagaContext context);

    /**
     * Create a new saga (alias for save, used by SagaEngine).
     * 
     * @param context The saga context to create
     * @return true if created successfully
     */
    default boolean create(SagaContext context) {
        return save(context);
    }

    /**
     * Find a saga by its internal ID.
     * 
     * @param orderId The internal order ID
     * @return Optional containing the saga if found
     */
    default Optional<SagaContext> findById(long orderId) {
        return findByOrderId(orderId);
    }

    /**
     * Update the status of an existing saga.
     * 
     * WHY SEPARATE METHOD:
     * - Status updates are frequent and should be fast
     * - Can use optimistic locking for concurrency
     * - Allows audit trail of status changes
     * 
     * @param context The context with updated status
     * @return true if updated successfully
     */
    boolean updateStatus(SagaContext context);

    /**
     * Find a saga by order ID.
     * 
     * @param orderId The order ID to search for
     * @return Optional containing the saga if found
     */
    Optional<SagaContext> findByOrderId(long orderId);

    /**
     * Find a saga by order number (external reference).
     * 
     * @param orderNo The external order number
     * @return Optional containing the saga if found
     */
    Optional<SagaContext> findByOrderNo(String orderNo);

    /**
     * Save a step execution log.
     * 
     * @param stepLog The step log to save
     * @return true if saved successfully
     */
    boolean saveStepLog(StepLog stepLog);

    /**
     * Get all step logs for a saga.
     * 
     * WHY: Used during recovery to reconstruct saga state.
     * 
     * @param orderId The order ID
     * @return List of step logs in execution order
     */
    List<StepLog> findStepLogsByOrderId(long orderId);

    /**
     * Find sagas that need recovery processing.
     * 
     * WHY: After system restart, we need to find sagas that were
     * in-progress and resume them.
     * 
     * Criteria for recovery:
     * - Not in terminal state
     * - Updated more than X minutes ago (stale)
     * 
     * @param limitMinutes Age threshold in minutes
     * @param maxResults   Maximum number of results
     * @return List of saga contexts needing recovery
     */
    List<SagaContext> findSagasForRecovery(int limitMinutes, int maxResults);

    /**
     * Check if a saga with the same order number exists.
     * 
     * WHY: Duplicate detection - prevent processing same order twice.
     * 
     * @param orderNo The external order number
     * @return true if a saga already exists
     */
    boolean existsByOrderNo(String orderNo);

    /**
     * Lock a saga for processing (pessimistic lock).
     * 
     * WHY: Prevent multiple threads/instances from processing
     * the same saga simultaneously.
     * 
     * @param orderId The order ID to lock
     * @return true if lock acquired
     */
    boolean tryLock(long orderId);

    /**
     * Release the lock on a saga.
     * 
     * @param orderId The order ID to unlock
     */
    void releaseLock(long orderId);
}
