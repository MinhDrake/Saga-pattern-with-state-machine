package com.learning.saga.application.saga;

import com.learning.saga.domain.model.saga.StepResult;
import lombok.Builder;
import lombok.Data;

/**
 * Command object for resuming a paused saga.
 * 
 * WHEN USED:
 * ==========
 * 1. Callback received from external system (payment gateway webhook)
 * 2. Recovery job picks up stuck saga
 * 3. Manual intervention to continue saga
 */
@Data
@Builder
public class ResumeSagaCommand {

    /**
     * The order ID to resume.
     */
    private final long orderId;

    /**
     * The step ID that triggered the resume (if from callback).
     */
    private final String stepId;

    /**
     * The result from the callback/external system.
     */
    private final StepResult callbackResult;

    /**
     * Whether this is from a recovery job (vs callback).
     */
    private final boolean isRecovery;

    /**
     * Source of the resume request (for logging).
     */
    private final String source;
}
