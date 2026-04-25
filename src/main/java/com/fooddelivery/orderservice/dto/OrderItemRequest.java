package com.fooddelivery.orderservice.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Represents a single line item in a create order request.
 */
public record OrderItemRequest(

        @NotBlank(message = "menuItemId must not be blank")
        String menuItemId,

        @NotBlank(message = "name must not be blank")
        String name,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity,

        @NotNull(message = "unitPrice is required")
        @DecimalMin(value = "0.00", inclusive = true, message = "unitPrice must be zero or positive")
        BigDecimal unitPrice
) {}