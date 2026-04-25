package com.fooddelivery.orderservice.exception;

/**
 * Thrown when publishing an event to Kafka fails.
 * OutboxPoller catches this and retries on the next poll cycle.
 */
public class KafkaPublishException extends RuntimeException {

    public KafkaPublishException(String message, Throwable cause) {
        super(message, cause);
    }
}