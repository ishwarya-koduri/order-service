package com.fooddelivery.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.orderservice.config.ApplicationLogger;
import com.fooddelivery.orderservice.dto.CreateOrderRequest;
import com.fooddelivery.orderservice.dto.ErrorResponse;
import com.fooddelivery.orderservice.dto.OrderResponse;
import com.fooddelivery.orderservice.dto.UpdateOrderStatusRequest;
import com.fooddelivery.orderservice.filter.AuthFilter;
import com.fooddelivery.orderservice.service.OrderService;
import com.fooddelivery.orderservice.dto.IdempotencyResult;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * Handles HTTP requests for order operations.
 * Delegates all business logic to OrderService.
 */
@RestController
@RequestMapping("/api/v1/orders")
@RequiredArgsConstructor
public class OrderController implements OrderApi {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(OrderController.class);

    private final OrderService orderService;
    private final ObjectMapper objectMapper;

    @Override
    @PostMapping
    public ResponseEntity<?> placeOrder(
            @RequestHeader(AuthFilter.CUSTOMER_ID_HEADER) String customerId,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request
    ) {
        log.info("POST /api/v1/orders customerId={} idempotencyKey={}", customerId, idempotencyKey);

        IdempotencyResult result = orderService.placeOrder(
                idempotencyKey,
                UUID.fromString(customerId),
                request
        );

        try {
            OrderResponse responseBody = objectMapper.readValue(
                    result.responseBody(), OrderResponse.class
            );
            return ResponseEntity.status(result.httpStatus()).body(responseBody);
        } catch (Exception e) {
            log.error("Failed to deserialize idempotency response for key={}", idempotencyKey, e);
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(ErrorResponse.of(500, "Internal Server Error",
                            "Failed to process response", "/api/v1/orders"));
        }
    }

    @Override
    @PatchMapping("/{orderId}/status")
    public ResponseEntity<OrderResponse> updateStatus(
            @RequestHeader(AuthFilter.CUSTOMER_ID_HEADER) String customerId,
            @PathVariable UUID orderId,
            @Valid @RequestBody UpdateOrderStatusRequest request
    ) {
        log.info("PATCH /api/v1/orders/{}/status customerId={} newStatus={}",
                orderId, customerId, request.status());

        OrderResponse response = orderService.updateStatus(
                orderId,
                UUID.fromString(customerId),
                request
        );

        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping("/{orderId}")
    public ResponseEntity<OrderResponse> getOrder(
            @RequestHeader(AuthFilter.CUSTOMER_ID_HEADER) String customerId,
            @PathVariable UUID orderId
    ) {
        log.info("GET /api/v1/orders/{} customerId={}", orderId, customerId);

        OrderResponse response = orderService.getOrder(orderId, UUID.fromString(customerId));
        return ResponseEntity.ok(response);
    }

    @Override
    @GetMapping
    public ResponseEntity<Page<OrderResponse>> listOrders(
            @RequestHeader(AuthFilter.CUSTOMER_ID_HEADER) String customerId,
            @RequestParam(required = false) UUID filterCustomerId,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        UUID effectiveCustomerId = filterCustomerId != null
                ? filterCustomerId
                : UUID.fromString(customerId);

        int effectiveSize = Math.min(size, 100);

        log.info("GET /api/v1/orders customerId={} status={} page={} size={}",
                effectiveCustomerId, status, page, effectiveSize);

        Page<OrderResponse> response = orderService.listOrders(
                effectiveCustomerId,
                status,
                PageRequest.of(page, effectiveSize)
        );

        return ResponseEntity.ok(response);
    }
}