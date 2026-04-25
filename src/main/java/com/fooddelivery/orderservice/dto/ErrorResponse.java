package com.fooddelivery.orderservice.dto;

import java.time.Instant;
import java.util.List;

/**
 * Standard error response returned by GlobalExceptionHandler for all error scenarios.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        List<String> details,
        Instant timestamp,
        String path
) {
    public static ErrorResponse of(int status, String error, String message, String path) {
        return new ErrorResponse(status, error, message, List.of(), Instant.now(), path);
    }

    public static ErrorResponse withDetails(
            int status,
            String error,
            String message,
            List<String> details,
            String path
    ) {
        return new ErrorResponse(status, error, message, details, Instant.now(), path);
    }
}