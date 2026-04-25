package com.fooddelivery.orderservice.exception;

import java.util.UUID;

// Maps to HTTP 404 Not Found via GlobalExceptionHandler.
public class OrderNotFoundException extends RuntimeException {

    public OrderNotFoundException(UUID orderId) {
        super("Order not found: " + orderId);
    }
}