package com.fooddelivery.orderservice.exception;

/**
 * Thrown when an outbox event or response object fails to serialize to JSON.
 */
public class EventSerializationException extends RuntimeException {

    public EventSerializationException(String message, Throwable cause) {
        super(message, cause);
    }
}