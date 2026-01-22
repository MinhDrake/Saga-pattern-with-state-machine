package com.learning.saga.domain.model.type;

/**
 * StepAction defines the type of operation a saga step performs.
 * 
 * WHY THIS DESIGN:
 * ================
 * In an e-commerce order flow, a saga typically involves:
 * 1. DEDUCT operations (take something away): Reserve inventory, charge payment
 * 2. ADD operations (give something): Create shipping order, send notification
 * 3. COMPENSATE operations (undo): Refund payment, release inventory
 * 
 * COMPENSATION MAPPING:
 * =====================
 * Each forward action has a corresponding compensation action:
 * - RESERVE_INVENTORY → RELEASE_INVENTORY
 * - CHARGE_PAYMENT → REFUND_PAYMENT
 * - CREATE_SHIPMENT → CANCEL_SHIPMENT
 * 
 * WHY NOT JUST "EXECUTE" AND "COMPENSATE":
 * ========================================
 * Being specific about action types helps with:
 * 1. Logging and monitoring (know what's happening)
 * 2. Metrics (count payments vs shipments)
 * 3. Different retry strategies per action type
 */
public enum StepAction {

    // ============ INVENTORY OPERATIONS ============
    /** Reserve inventory for the order */
    RESERVE_INVENTORY,
    
    /** Release previously reserved inventory (compensation) */
    RELEASE_INVENTORY,

    // ============ PAYMENT OPERATIONS ============
    /** Charge customer's payment method */
    CHARGE_PAYMENT,
    
    /** Refund the charged amount (compensation) */
    REFUND_PAYMENT,

    // ============ SHIPPING OPERATIONS ============
    /** Create shipment/delivery order */
    CREATE_SHIPMENT,
    
    /** Cancel the shipment order (compensation) */
    CANCEL_SHIPMENT,

    // ============ NOTIFICATION OPERATIONS ============
    /** Send order confirmation to customer */
    SEND_NOTIFICATION,

    // ============ GENERIC OPERATIONS ============
    /** Generic compensating action */
    COMPENSATE,
    
    /** Query the status of a previous operation */
    QUERY;

    /**
     * Returns the compensation action for this action.
     * 
     * WHY: When saga fails, we need to know what compensation to run
     * for each completed step.
     */
    public StepAction getCompensationAction() {
        return switch (this) {
            case RESERVE_INVENTORY -> RELEASE_INVENTORY;
            case CHARGE_PAYMENT -> REFUND_PAYMENT;
            case CREATE_SHIPMENT -> CANCEL_SHIPMENT;
            case SEND_NOTIFICATION -> null; // Notifications can't be "unsent"
            default -> COMPENSATE;
        };
    }

    /**
     * Check if this action requires compensation on failure.
     * Some actions (like notifications) don't need compensation.
     */
    public boolean requiresCompensation() {
        return getCompensationAction() != null;
    }

    /**
     * Check if this action is a compensation action.
     */
    public boolean isCompensation() {
        return this == RELEASE_INVENTORY 
            || this == REFUND_PAYMENT 
            || this == CANCEL_SHIPMENT
            || this == COMPENSATE;
    }
}
