package com.learning.saga.application.saga;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Command object for starting a new saga.
 * 
 * WHY COMMAND OBJECT:
 * ===================
 * 1. Encapsulates all data needed to start a saga
 * 2. Immutable - can't be modified after creation
 * 3. Easy to validate
 * 4. Clear API contract
 */
@Data
@Builder
public class StartSagaCommand {

    /**
     * External order number (from client).
     */
    private final String orderNo;

    /**
     * Customer ID placing the order.
     */
    private final long customerId;

    /**
     * Order items to process.
     */
    private final List<OrderItem> items;

    /**
     * Payment method details.
     */
    private final PaymentInfo payment;

    /**
     * Shipping address.
     */
    private final ShippingInfo shipping;

    /**
     * Additional metadata.
     */
    private final Map<String, Object> metadata;

    /**
     * Order item details.
     */
    @Data
    @Builder
    public static class OrderItem {
        private final String productId;
        private final String productName;
        private final int quantity;
        private final long unitPrice;
        private final long totalPrice;
    }

    /**
     * Payment information.
     */
    @Data
    @Builder
    public static class PaymentInfo {
        private final String method; // CARD, WALLET, BANK_TRANSFER
        private final String accountId;
        private final long amount;
        private final String currency;
    }

    /**
     * Shipping information.
     */
    @Data
    @Builder
    public static class ShippingInfo {
        private final String recipientName;
        private final String address;
        private final String city;
        private final String postalCode;
        private final String phone;
    }
}
