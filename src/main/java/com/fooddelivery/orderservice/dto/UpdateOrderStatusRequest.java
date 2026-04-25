package com.fooddelivery.orderservice.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request body for updating an order status.
 */
public record UpdateOrderStatusRequest(

        @NotBlank(message = "status must not be blank")
        String status
) {}