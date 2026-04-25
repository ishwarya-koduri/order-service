package com.fooddelivery.orderservice.dto;

import com.fooddelivery.orderservice.model.Order;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Response representation of an order with its items.
 */
public record OrderResponse(
        UUID id,
        UUID customerId,
        String status,
        BigDecimal totalAmount,
        List<OrderItemResponse> items,
        Instant createdAt,
        Instant updatedAt,
        Integer version
) {

}