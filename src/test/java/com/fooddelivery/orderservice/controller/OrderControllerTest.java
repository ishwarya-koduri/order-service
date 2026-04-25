package com.fooddelivery.orderservice.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fooddelivery.orderservice.dto.*;
import com.fooddelivery.orderservice.exception.GlobalExceptionHandler;
import com.fooddelivery.orderservice.exception.InvalidStatusTransitionException;
import com.fooddelivery.orderservice.exception.OrderNotFoundException;
import com.fooddelivery.orderservice.exception.OrderOwnershipException;
import com.fooddelivery.orderservice.model.OrderStatus;
import com.fooddelivery.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderController — HTTP Layer")
class OrderControllerTest {

    @Mock
    private OrderService orderService;

    private OrderController orderController;
    private MockMvc mockMvc;
    private ObjectMapper objectMapper;

    private UUID customerId;
    private UUID orderId;
    private OrderResponse mockResponse;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Clean constructor injection — no @InjectMocks needed
        orderController = new OrderController(orderService, objectMapper);

        mockMvc = MockMvcBuilders
                .standaloneSetup(orderController)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        customerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        orderId    = UUID.randomUUID();

        mockResponse = new OrderResponse(
                orderId,
                customerId,
                "PENDING",
                new BigDecimal("30.97"),
                new ArrayList<>(),
                Instant.now(),
                Instant.now(),
                0
        );
    }

    // ── POST /api/v1/orders ───────────────────────────────────────────────

    @Test
    @DisplayName("POST /orders — 201 on successful order placement")
    void placeOrder_validRequest_returns201() throws Exception {
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(orderService.placeOrder(any(), any(), any()))
                .thenReturn(IdempotencyResult.fresh(201, responseJson));

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("item-001", "Burger", 1, new BigDecimal("10.00"))
        ));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .header("Idempotency-Key", "test-key-001")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(30.97));
    }

    @Test
    @DisplayName("POST /orders — 201 on idempotency replay with same orderId")
    void placeOrder_duplicateKey_returnsSameOrderId() throws Exception {
        String responseJson = objectMapper.writeValueAsString(mockResponse);

        when(orderService.placeOrder(any(), any(), any()))
                .thenReturn(IdempotencyResult.replay(201, responseJson));

        CreateOrderRequest request = new CreateOrderRequest(List.of(
                new OrderItemRequest("item-001", "Burger", 1, new BigDecimal("10.00"))
        ));

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .header("Idempotency-Key", "test-key-001")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(orderId.toString()));
    }

    @Test
    @DisplayName("POST /orders — 400 when items list is empty")
    void placeOrder_emptyItems_returns400() throws Exception {
        CreateOrderRequest request = new CreateOrderRequest(new ArrayList<>());

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .header("Idempotency-Key", "test-key-001")
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    @Test
    @DisplayName("POST /orders — 400 when required item fields are missing")
    void placeOrder_invalidItemFields_returns400WithDetails() throws Exception {
        String body = """
                {
                  "items": [
                    {
                      "menuItemId": "",
                      "name": "",
                      "quantity": 0,
                      "unitPrice": -1
                    }
                  ]
                }
                """;

        mockMvc.perform(post("/api/v1/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .header("Idempotency-Key", "test-key-001")
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.details").isArray())
                .andExpect(jsonPath("$.details.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    // ── PATCH /api/v1/orders/{id}/status ──────────────────────────────────

    @Test
    @DisplayName("PATCH /orders/{id}/status — 200 on valid transition")
    void updateStatus_validTransition_returns200() throws Exception {
        OrderResponse confirmedResponse = new OrderResponse(
                orderId, customerId, "CONFIRMED",
                new BigDecimal("30.97"), new ArrayList<>(),
                Instant.now(), Instant.now(), 1
        );

        when(orderService.updateStatus(eq(orderId), any(), any()))
                .thenReturn(confirmedResponse);

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .content(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest("CONFIRMED"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.version").value(1));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/status — 404 when order not found")
    void updateStatus_orderNotFound_returns404() throws Exception {
        when(orderService.updateStatus(eq(orderId), any(), any()))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .content(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest("CONFIRMED"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/status — 403 when wrong customer")
    void updateStatus_wrongCustomer_returns403() throws Exception {
        when(orderService.updateStatus(eq(orderId), any(), any()))
                .thenThrow(new OrderOwnershipException(orderId, customerId));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .content(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest("CONFIRMED"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403))
                .andExpect(jsonPath("$.error").value("Forbidden"));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/status — 422 on invalid transition")
    void updateStatus_invalidTransition_returns422() throws Exception {
        when(orderService.updateStatus(eq(orderId), any(), any()))
                .thenThrow(new InvalidStatusTransitionException(
                        orderId, OrderStatus.DELIVERED, OrderStatus.CANCELLED));

        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .content(objectMapper.writeValueAsString(
                                new UpdateOrderStatusRequest("CANCELLED"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.status").value(422))
                .andExpect(jsonPath("$.error").value("Unprocessable Entity"));
    }

    @Test
    @DisplayName("PATCH /orders/{id}/status — 400 on blank status value")
    void updateStatus_blankStatus_returns400() throws Exception {
        mockMvc.perform(patch("/api/v1/orders/{id}/status", orderId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Customer-Id", customerId.toString())
                        .content("{\"status\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/v1/orders/{id} ───────────────────────────────────────────

    @Test
    @DisplayName("GET /orders/{id} — 200 with full order")
    void getOrder_existingOrder_returns200() throws Exception {
        when(orderService.getOrder(eq(orderId), any()))
                .thenReturn(mockResponse);

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("X-Customer-Id", customerId.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.totalAmount").value(30.97));
    }

    @Test
    @DisplayName("GET /orders/{id} — 404 when order not found")
    void getOrder_notFound_returns404() throws Exception {
        when(orderService.getOrder(eq(orderId), any()))
                .thenThrow(new OrderNotFoundException(orderId));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("X-Customer-Id", customerId.toString()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").isNotEmpty());
    }

    @Test
    @DisplayName("GET /orders/{id} — 403 when wrong customer")
    void getOrder_wrongCustomer_returns403() throws Exception {
        when(orderService.getOrder(eq(orderId), any()))
                .thenThrow(new OrderOwnershipException(orderId, customerId));

        mockMvc.perform(get("/api/v1/orders/{id}", orderId)
                        .header("X-Customer-Id", customerId.toString()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    @DisplayName("GET /orders/{id} — 400 when orderId is not a valid UUID")
    void getOrder_invalidUuid_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/orders/{id}", "not-a-uuid")
                        .header("X-Customer-Id", customerId.toString()))
                .andExpect(status().isBadRequest());
    }
}