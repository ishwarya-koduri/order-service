package com.fooddelivery.orderservice.dto;

/**
 * Carries the HTTP status and response body back to the controller
 * after an idempotency check on order placement.
 */
public record IdempotencyResult(int httpStatus, String responseBody, boolean isReplay) {

    public static IdempotencyResult fresh(int httpStatus, String body) {
        return new IdempotencyResult(httpStatus, body, false);
    }

    public static IdempotencyResult replay(int httpStatus, String body) {
        return new IdempotencyResult(httpStatus, body, true);
    }
}