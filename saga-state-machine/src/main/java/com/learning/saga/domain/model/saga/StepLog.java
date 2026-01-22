package com.learning.saga.domain.model.saga;

import com.learning.saga.domain.model.type.StepAction;
import com.learning.saga.domain.model.type.StepStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * StepLog is a serializable representation of a SagaStep for persistence.
 * 
 * WHY SEPARATE FROM SagaStep:
 * ===========================
 * 1. SagaStep has behavior (execute, query), StepLog is pure data
 * 2. StepLog is designed for database storage and JSON serialization
 * 3. When recovering, we load StepLog and reconstruct SagaStep
 * 
 * PERSISTENCE STRATEGY:
 * =====================
 * - Store in database table: saga_step_{YYYYMM}
 * - Partition by month for efficient queries
 * - Index on: order_id, status, updated_at
 */
@Data
@Builder
public class StepLog {

    /**
     * Unique identifier: {orderId}:{index}:{action}:{serviceType}
     */
    private String stepId;

    /**
     * Parent order ID.
     */
    private long orderId;

    /**
     * Position in saga sequence.
     */
    private int index;

    /**
     * Type of action performed.
     */
    private StepAction action;

    /**
     * Current status of the step.
     */
    private StepStatus status;

    /**
     * Error code if failed.
     */
    private Integer errorCode;

    /**
     * Error message if failed.
     */
    private String errorMessage;

    /**
     * External reference ID for reconciliation.
     */
    private String externalRefId;

    /**
     * Additional metadata as JSON.
     */
    private String metadata;

    /**
     * When this step was created.
     */
    private Instant createdAt;

    /**
     * When this step was last updated.
     */
    private Instant updatedAt;

    /**
     * When the request was sent to external service.
     */
    private Instant sentAt;

    /**
     * When the response was received.
     */
    private Instant receivedAt;

    /**
     * Flag for compensation steps.
     */
    private boolean isCompensationStep;

    /**
     * If this is a compensation step, which step it compensates for.
     */
    private String compensatesForStepId;
}
