package com.fooddelivery.orderservice.exception;

/**
 * Thrown when persisting an order or its related data to the database fails.
 * Signals to the client that the operation failed and a retry may be safe.
 */
public class OrderPersistenceException extends RuntimeException {

    public OrderPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}