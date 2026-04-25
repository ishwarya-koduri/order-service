package com.fooddelivery.orderservice.mapper;

import com.fooddelivery.orderservice.dto.ErrorResponse;
import com.fooddelivery.orderservice.dto.OrderItemResponse;
import com.fooddelivery.orderservice.dto.OrderResponse;
import com.fooddelivery.orderservice.kafka.OrderStatusChangedEvent;
import com.fooddelivery.orderservice.model.Order;
import com.fooddelivery.orderservice.model.OrderItem;
import com.fooddelivery.orderservice.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Centralized mapper for converting domain objects to DTOs and Kafka events.
 * Single place for all mapping logic — no mapping code scattered across classes.
 */
@Component
public class OrderMapper {

    /**
     * Maps Order domain object to OrderResponse DTO.
     */
    public OrderResponse toOrderResponse(Order order) {
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                toOrderItemResponses(order),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                order.getVersion()
        );
    }

    /**
     * Maps OrderItem domain object to OrderItemResponse DTO.
     */
    public OrderItemResponse toOrderItemResponse(OrderItem item) {
        return new OrderItemResponse(
                item.getId(),
                item.getMenuItemId(),
                item.getName(),
                item.getQuantity(),
                item.getUnitPrice(),
                item.subtotal()
        );
    }

    /**
     * Builds an OrderStatusChangedEvent for Kafka publishing.
     */
    public OrderStatusChangedEvent toOrderStatusChangedEvent(
            Order order,
            OrderStatus previousStatus,
            OrderStatus newStatus
    ) {
        return new OrderStatusChangedEvent(
                UUID.randomUUID().toString(),
                order.getId().toString(),
                order.getCustomerId().toString(),
                previousStatus != null ? previousStatus.name() : null,
                newStatus.name(),
                Instant.now().toString()
        );
    }

    /**
     * Builds a simple ErrorResponse with no field-level details.
     */
    public ErrorResponse toErrorResponse(int status, String error, String message, String path) {
        return ErrorResponse.of(status, error, message, path);
    }

    /**
     * Builds an ErrorResponse with field-level validation details.
     */
    public ErrorResponse toErrorResponseWithDetails(
            int status,
            String error,
            String message,
            List<String> details,
            String path
    ) {
        return ErrorResponse.withDetails(status, error, message, details, path);
    }

    private List<OrderItemResponse> toOrderItemResponses(Order order) {
        return order.getItems().stream()
                .map(this::toOrderItemResponse)
                .toList();
    }
}