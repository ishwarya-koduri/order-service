package com.fooddelivery.orderservice.exception;

import com.fooddelivery.orderservice.model.OrderStatus;

import java.util.UUID;

// Maps to HTTP 422 Unprocessable Entity via GlobalExceptionHandler.
// Thrown when a caller requests a transition that is not in VALID_TRANSITIONS.
// Example: DELIVERED → CANCELLED is not valid → this exception is thrown.
public class InvalidStatusTransitionException extends RuntimeException {

    private final UUID orderId;
    private final OrderStatus currentStatus;
    private final OrderStatus requestedStatus;

    public InvalidStatusTransitionException(
            UUID orderId,
            OrderStatus currentStatus,
            OrderStatus requestedStatus
    ) {
        super(String.format(
                "Cannot transition order %s from %s to %s",
                orderId, currentStatus, requestedStatus
        ));
        this.orderId         = orderId;
        this.currentStatus   = currentStatus;
        this.requestedStatus = requestedStatus;
    }

    public UUID getOrderId()             { return orderId; }
    public OrderStatus getCurrentStatus()   { return currentStatus; }
    public OrderStatus getRequestedStatus() { return requestedStatus; }
}