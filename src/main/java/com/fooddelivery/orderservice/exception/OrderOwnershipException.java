package com.fooddelivery.orderservice.exception;

import java.util.UUID;

// Maps to HTTP 403 Forbidden via GlobalExceptionHandler.
// Thrown when a customer tries to access or cancel another customer's order.
// We deliberately do NOT reveal that the order exists —
// just that the caller is not authorized.
public class OrderOwnershipException extends RuntimeException {

    public OrderOwnershipException(UUID orderId, UUID requestingCustomerId) {
        super(String.format(
                "Customer %s is not authorized to access order %s",
                requestingCustomerId, orderId
        ));
    }
}