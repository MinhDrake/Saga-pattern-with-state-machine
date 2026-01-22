package com.learning.saga.domain.model.saga;

import com.learning.saga.domain.model.type.OrderStatus;
import com.learning.saga.domain.model.type.StepAction;
import com.learning.saga.domain.model.type.StepStatus;
import lombok.Data;
import lombok.experimental.Accessors;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

/**
 * SagaContext is the central orchestrator for a saga instance.
 * It holds all transaction data and controls the state machine progression.
 * 
 * WHY SAGACONTEXT:
 * ================
 * 1. SINGLE SOURCE OF TRUTH: All saga state in one place
 * 2. ENCAPSULATION: Steps and status are managed together
 * 3. THREAD SAFETY: Lock ensures consistent state changes
 * 4. RECOVERY: Contains all info needed to resume after failure
 * 
 * KEY PRINCIPLES:
 * ===============
 * 1. Only StateHandler can modify SagaContext (except initialization)
 * 2. Status transitions follow the state machine rules
 * 3. Steps are executed in order, tracked by currentStep index
 * 4. Timeout management is built-in for saga expiration
 * 
 * LIFECYCLE:
 * ==========
 * 1. Create SagaContext with order details
 * 2. Call initSaga() to set up steps from order items
 * 3. StateHandler processes context through state transitions
 * 4. Context reaches terminal state (SUCCESS, FAILED, REVERTED, etc.)
 */
@Slf4j
@Data
@Accessors(chain = true)
public class SagaContext {

    // ============ CONSTANTS ============
    
    /** Marker for unstarted saga */
    public static final int BEGIN_STEP = -1;
    
    /** Minimum time reserved for compensation */
    public static final Duration MIN_TIMEOUT_FOR_REVERT = Duration.ofMinutes(5);

    // ============ IDENTITY ============
    
    /** Unique order identifier */
    private long orderId;
    
    /** External order reference (from client) */
    private String orderNo;
    
    /** Customer identifier */
    private long customerId;

    // ============ STATE ============
    
    /** Current saga status - controlled by StateHandler */
    private OrderStatus status;
    
    /** Error/reason information for current state */
    private StepResult lastResult;

    // ============ SAGA STEPS ============
    
    /** Ordered list of forward saga steps to execute */
    private List<SagaStep> steps = new ArrayList<>();
    
    /** Map of stepId -> SagaStep for quick lookup */
    private Map<String, SagaStep> stepMap = new HashMap<>();
    
    /** Current step index being processed (forward flow) */
    private int currentStep = BEGIN_STEP;
    
    /** Current compensation step index (reverse flow) */
    private int currentCompensationStep = -1;
    
    /** IDs of steps that have been processed */
    private List<String> processedStepIds = new ArrayList<>();
    
    /** 
     * Compensation steps to execute during reverting.
     * 
     * WHY A LIST:
     * ===========
     * Each succeeded forward step needs its own compensation:
     * - RESERVE_INVENTORY succeeded → needs RELEASE_INVENTORY
     * - CHARGE_PAYMENT succeeded → needs REFUND_PAYMENT
     * 
     * These are executed in REVERSE order of the forward steps.
     * 
     * ALTERNATIVE DESIGN (used in some systems):
     * A single "RevertStep" that calls an external API to revert everything at once.
     * This is simpler but less flexible.
     */
    private List<SagaStep> compensationSteps = new ArrayList<>();

    // ============ TIMEOUT ============
    
    /** Maximum time allowed for this saga */
    private Duration timeout = Duration.ofMinutes(30);
    
    /** When this saga was created */
    private Instant createdAt = Instant.now();
    
    /** When this saga was last updated */
    private Instant updatedAt = Instant.now();

    // ============ METADATA ============
    
    /** Additional context data */
    private Map<String, Object> metadata = new HashMap<>();
    
    /** Whether compensation is allowed for this saga */
    private boolean compensationAllowed = true;

    // ============ THREAD SAFETY ============
    
    /** Lock for thread-safe initialization */
    private final transient ReentrantLock initLock = new ReentrantLock();

    // ============ STATUS MANAGEMENT ============

    /**
     * Set the saga status with logging.
     * 
     * WHY CUSTOM SETTER:
     * - Logs all state transitions for debugging
     * - Automatically updates the updatedAt timestamp
     */
    public SagaContext setStatus(OrderStatus newStatus) {
        if (this.status != null) {
            log.info("[Saga:{}] Status transition: {} -> {}", orderId, this.status, newStatus);
        }
        this.status = newStatus;
        this.updatedAt = Instant.now();
        return this;
    }

    // ============ INITIALIZATION ============

    /**
     * Initialize the saga with steps based on order items.
     * 
     * WHY SEPARATE INIT:
     * - Allows loading existing steps when recovering
     * - Steps can be constructed from different sources
     * 
     * @param existingSteps Previously persisted steps (for recovery)
     * @return this context for chaining
     */
    public SagaContext initSaga(List<StepLog> existingSteps) {
        initLock.lock();
        try {
            var existingStepMap = existingSteps.stream()
                .collect(Collectors.toMap(StepLog::getStepId, s -> s));

            // Build step map for quick lookup
            stepMap = new HashMap<>();
            for (var step : steps) {
                stepMap.put(step.getStepId(), step);
            }

            // Compute current step based on what's already processed
            currentStep = computeCurrentStep(existingSteps);
            
            // Track processed steps
            processedStepIds = steps.stream()
                .map(SagaStep::getStepId)
                .filter(existingStepMap::containsKey)
                .collect(Collectors.toCollection(ArrayList::new));

            return this;
        } finally {
            initLock.unlock();
        }
    }

    /**
     * Initialize saga without existing steps (new saga).
     */
    public SagaContext initSaga() {
        return initSaga(List.of());
    }

    /**
     * Compute which step to start from based on existing steps.
     * 
     * WHY: When recovering, we don't want to re-execute completed steps.
     */
    private int computeCurrentStep(List<StepLog> existingSteps) {
        if (isReverting()) {
            return steps.size(); // Skip to compensation
        }
        return existingSteps.isEmpty() ? BEGIN_STEP : existingSteps.size() - 1;
    }

    // ============ STEP NAVIGATION ============

    /**
     * Get the next step to execute and advance the pointer.
     * 
     * WHY ADVANCE HERE:
     * - Ensures step is tracked as processed
     * - Single place to manage step progression
     */
    public SagaStep getNextStep() {
        var nextStep = steps.get(++currentStep);
        processedStepIds.add(nextStep.getStepId());
        return nextStep;
    }

    /**
     * Get current step without advancing.
     * 
     * Returns the appropriate step based on saga state:
     * - Forward flow: returns current forward step
     * - Reverting flow: returns current compensation step
     */
    public SagaStep getCurrentStep() {
        if (isReverting()) {
            // During reverting, return current compensation step
            if (currentCompensationStep < 0 || currentCompensationStep >= compensationSteps.size()) {
                return null;
            }
            return compensationSteps.get(currentCompensationStep);
        }
        
        // Forward flow
        if (currentStep < 0 || currentStep >= steps.size()) {
            return null;
        }
        return steps.get(currentStep);
    }
    
    /**
     * Get the next compensation step to execute and advance the pointer.
     */
    public SagaStep getNextCompensationStep() {
        if (compensationSteps.isEmpty()) {
            return null;
        }
        return compensationSteps.get(++currentCompensationStep);
    }
    
    /**
     * Check if there are more compensation steps to execute.
     */
    public boolean hasMoreCompensationSteps() {
        return currentCompensationStep + 1 < compensationSteps.size();
    }

    /**
     * Check if we've processed all steps (forward or compensation).
     * 
     * For forward flow: checks if last forward step succeeded
     * For reverting: checks if all compensation steps completed
     */
    public boolean isLastStep() {
        if (isReverting()) {
            // All compensation steps must be processed
            if (compensationSteps.isEmpty()) {
                return true; // Nothing to compensate
            }
            var lastCompStep = compensationSteps.get(compensationSteps.size() - 1);
            return lastCompStep.getStatus() == StepStatus.SUCCEEDED;
        }
        
        // Forward flow
        if (steps.isEmpty()) return true;
        var lastStep = steps.get(steps.size() - 1);
        return lastStep.getStatus() == StepStatus.SUCCEEDED;
    }

    // ============ STATE QUERIES ============

    /**
     * Check if saga is in reverting phase.
     */
    public boolean isReverting() {
        return status == OrderStatus.REVERTING
            || status == OrderStatus.RESUMING_REVERTING
            || status == OrderStatus.REVERTING_PENDING
            || status == OrderStatus.RECOVERY_REVERTING;
    }

    /**
     * Check if saga has reached a terminal state.
     * 
     * WHY: Terminal states mean no more processing needed.
     */
    public boolean isTerminal() {
        return status.isTerminal() || isTimeout();
    }

    /**
     * Check if saga has timed out.
     */
    public boolean isTimeout() {
        return Instant.now().isAfter(createdAt.plus(timeout));
    }

    /**
     * Get remaining time before timeout.
     */
    public Duration getRemainingTime() {
        var elapsed = Duration.between(createdAt, Instant.now());
        return timeout.minus(elapsed);
    }

    // ============ FAILURE HANDLING ============

    /**
     * Evaluate what status to transition to after a step failure.
     * 
     * WHY THIS LOGIC:
     * - If first step fails → FAILED (nothing to compensate)
     * - If later step fails → check if we can/should compensate
     * - If any ADD step succeeded → MANUAL_REVIEW (partial success)
     * 
     * @return The appropriate next status
     */
    public OrderStatus evaluateFailedStep() {
        // First step failed - nothing to compensate
        if (steps.isEmpty() || steps.get(0).getStatus() == StepStatus.FAILED) {
            return OrderStatus.FAILED;
        }

        // Check if any step that "adds" value succeeded
        // If so, we need manual review (can't auto-compensate partial success)
        boolean hasSuccessfulAddStep = steps.stream()
            .filter(s -> !s.getAction().isCompensation())
            .filter(s -> s.getAction() == StepAction.CREATE_SHIPMENT 
                      || s.getAction() == StepAction.SEND_NOTIFICATION)
            .anyMatch(s -> s.getStatus() == StepStatus.SUCCEEDED);

        if (hasSuccessfulAddStep) {
            return OrderStatus.MANUAL_REVIEW;
        }

        // Can we compensate?
        if (compensationAllowed && hasTimeForCompensation()) {
            extendTimeoutIfNeeded();
            return OrderStatus.REVERTING;
        }

        return OrderStatus.REVERT_FAILED;
    }

    /**
     * Check if there's enough time remaining for compensation.
     */
    private boolean hasTimeForCompensation() {
        return getRemainingTime().compareTo(MIN_TIMEOUT_FOR_REVERT) > 0;
    }

    /**
     * Extend timeout if needed for compensation.
     */
    private void extendTimeoutIfNeeded() {
        var remaining = getRemainingTime();
        if (remaining.compareTo(MIN_TIMEOUT_FOR_REVERT) < 0) {
            var extension = MIN_TIMEOUT_FOR_REVERT.minus(remaining);
            timeout = timeout.plus(extension);
            log.info("[Saga:{}] Extended timeout by {} for compensation", orderId, extension);
        }
    }

    // ============ STEP LOOKUP ============

    /**
     * Find a step by its ID.
     */
    public Optional<SagaStep> findStep(String stepId) {
        return Optional.ofNullable(stepMap.get(stepId));
    }

    /**
     * Get all processed steps as logs for persistence.
     */
    public List<StepLog> getProcessedStepLogs() {
        initLock.lock();
        try {
            return processedStepIds.stream()
                .map(stepMap::get)
                .filter(Objects::nonNull)
                .map(SagaStep::toLog)
                .sorted(Comparator.comparingInt(StepLog::getIndex))
                .collect(Collectors.toList());
        } finally {
            initLock.unlock();
        }
    }

    /**
     * Get forward steps that need compensation (succeeded steps in reverse order).
     * 
     * WHY REVERSE ORDER:
     * ==================
     * Forward: Reserve Inventory → Charge Payment → Create Shipment
     * Reverse: Cancel Shipment → Refund Payment → Release Inventory
     * 
     * Later steps may depend on earlier ones, so we undo in reverse.
     */
    public List<SagaStep> getStepsNeedingCompensation() {
        var result = new ArrayList<>(steps.stream()
            .filter(SagaStep::needsCompensation)
            .collect(Collectors.toList()));
        Collections.reverse(result); // Compensate in reverse order
        return result;
    }
    
    /**
     * Build compensation steps from succeeded forward steps.
     * 
     * This should be called when transitioning to REVERTING state.
     * Each forward step that succeeded gets a corresponding compensation step.
     * 
     * @param compensationStepFactory Factory to create compensation steps
     */
    public void buildCompensationSteps(java.util.function.Function<SagaStep, SagaStep> compensationStepFactory) {
        var stepsToCompensate = getStepsNeedingCompensation();
        
        this.compensationSteps = stepsToCompensate.stream()
            .map(compensationStepFactory)
            .filter(java.util.Objects::nonNull)
            .collect(Collectors.toList());
        
        this.currentCompensationStep = -1; // Reset to beginning
    }
    
    /**
     * Set compensation steps directly (for recovery scenarios).
     */
    public void setCompensationSteps(List<SagaStep> compensationSteps) {
        this.compensationSteps = new ArrayList<>(compensationSteps);
        this.currentCompensationStep = -1;
    }
    
    /**
     * Get the list of compensation steps.
     */
    public List<SagaStep> getCompensationSteps() {
        return Collections.unmodifiableList(compensationSteps);
    }
}
