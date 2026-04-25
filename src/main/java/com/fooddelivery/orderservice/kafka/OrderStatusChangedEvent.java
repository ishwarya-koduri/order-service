package com.fooddelivery.orderservice.kafka;

import com.fooddelivery.orderservice.model.Order;
import com.fooddelivery.orderservice.model.OrderStatus;

import java.time.Instant;
import java.util.UUID;

/**
 * Kafka event payload published on every order status change.
 * eventId is unique per event — used by consumers for deduplication.
 */
public record OrderStatusChangedEvent(
        String eventId,
        String orderId,
        String customerId,
        String previousStatus,
        String newStatus,
        String timestamp
) {
}