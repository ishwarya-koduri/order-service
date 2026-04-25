package com.fooddelivery.orderservice.controller;

import com.fooddelivery.orderservice.dto.CreateOrderRequest;
import com.fooddelivery.orderservice.dto.ErrorResponse;
import com.fooddelivery.orderservice.dto.OrderResponse;
import com.fooddelivery.orderservice.dto.UpdateOrderStatusRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;

import java.util.UUID;

/**
 * Contract interface for Order Service REST endpoints.
 * Contains only Swagger/OpenAPI documentation — no routing logic.
 */
@Tag(name = "Orders", description = "Order placement, status management and retrieval")
public interface OrderApi {

    @Operation(
            summary = "Place a new order",
            description = "Creates a new order in PENDING status. " +
                    "Requires a client-supplied Idempotency-Key header to safely handle retries. " +
                    "Submitting the same request twice with the same key returns the original response without creating a duplicate order."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Order created successfully",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request body or missing required fields",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "X-Customer-Id header is missing",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "Internal server error",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<?> placeOrder(String customerId, String idempotencyKey, CreateOrderRequest request);

    @Operation(
            summary = "Update order status",
            description = "Transitions an order to a new status. " +
                    "Valid transitions: PENDING → CONFIRMED → PREPARING → OUT_FOR_DELIVERY → DELIVERED. " +
                    "Cancellation allowed from PENDING and CONFIRMED only. " +
                    "Duplicate status requests are handled safely and return 200."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status updated successfully",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid status value",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "X-Customer-Id header is missing",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Customer does not own this order",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Concurrent update conflict — client should retry",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "422", description = "Invalid status transition",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderResponse> updateStatus(String customerId, UUID orderId, UpdateOrderStatusRequest request);

    @Operation(
            summary = "Get order by ID",
            description = "Fetches a single order with all its items. " +
                    "Only the customer who placed the order can retrieve it."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Order retrieved successfully",
                    content = @Content(schema = @Schema(implementation = OrderResponse.class))),
            @ApiResponse(responseCode = "401", description = "X-Customer-Id header is missing",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Customer does not own this order",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "Order not found",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<OrderResponse> getOrder(String customerId, UUID orderId);

    @Operation(
            summary = "List orders",
            description = "Returns a paginated list of orders. " +
                    "Defaults to listing orders for the requesting customer. " +
                    "Supports optional filtering by status."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Orders retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "X-Customer-Id header is missing",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    ResponseEntity<Page<OrderResponse>> listOrders(
            String customerId,
            UUID filterCustomerId,
            String status,
            int page,
            int size
    );
}