package com.fooddelivery.orderservice.dto;

import com.fooddelivery.orderservice.model.OrderItem;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Response representation of a single order line item.
 */
public record OrderItemResponse(
        UUID id,
        String menuItemId,
        String name,
        Integer quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal
) {

}