package com.fooddelivery.orderservice.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.orderservice.dto.IdempotencyResult;
import com.fooddelivery.orderservice.dto.OrderResponse;
import com.fooddelivery.orderservice.dto.UpdateOrderStatusRequest;
import com.fooddelivery.orderservice.exception.OrderNotFoundException;
import com.fooddelivery.orderservice.exception.OrderOwnershipException;
import com.fooddelivery.orderservice.exception.InvalidStatusTransitionException;
import com.fooddelivery.orderservice.kafka.OrderStatusChangedEvent;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.model.IdempotencyKeyEntity;
import com.fooddelivery.orderservice.model.Order;
import com.fooddelivery.orderservice.model.OrderStatus;
import com.fooddelivery.orderservice.model.OutboxEvent;
import com.fooddelivery.orderservice.repository.IdempotencyKeyRepository;
import com.fooddelivery.orderservice.repository.OrderJpaRepository;
import com.fooddelivery.orderservice.repository.OutboxRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — Business Logic")
class OrderServiceTest {

    @Mock private OrderJpaRepository        orderRepository;
    @Mock private IdempotencyKeyRepository  idempotencyKeyRepository;
    @Mock private OutboxRepository          outboxRepository;
    @Mock private OrderTransactionalService orderTransactionalService;
    @Mock private OrderMapper               orderMapper;
    @Mock private ObjectMapper              objectMapper;

    @InjectMocks
    private OrderService orderService;

    private UUID          customerId;
    private UUID          orderId;
    private Order         pendingOrder;
    private Order         confirmedOrder;
    private OrderResponse mockResponse;

    @BeforeEach
    void setUp() {
        customerId = UUID.fromString("550e8400-e29b-41d4-a716-446655440000");
        orderId    = UUID.randomUUID();

        pendingOrder = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .status(OrderStatus.PENDING)
                .totalAmount(new BigDecimal("30.97"))
                .items(new ArrayList<>())
                .build();

        confirmedOrder = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("30.97"))
                .items(new ArrayList<>())
                .build();

        mockResponse = new OrderResponse(
                orderId, customerId, "PENDING",
                new BigDecimal("30.97"), new ArrayList<>(),
                Instant.now(), Instant.now(), 0
        );
    }

    // ── placeOrder ─────────────────────────────────────────────────────────

    @Test
    @DisplayName("placeOrder — returns stored response on idempotency hit")
    void placeOrder_idempotencyHit_returnsStoredResponse() {
        IdempotencyKeyEntity stored = IdempotencyKeyEntity.builder()
                .idempotencyKey("test-key-001")
                .orderId(orderId)
                .httpStatus(201)
                .responseBody("{\"id\":\"" + orderId + "\"}")
                .build();

        when(idempotencyKeyRepository.findById("test-key-001"))
                .thenReturn(Optional.of(stored));

        IdempotencyResult result = orderService.placeOrder(
                "test-key-001", customerId, null
        );

        assertThat(result.isReplay()).isTrue();
        assertThat(result.httpStatus()).isEqualTo(201);
        assertThat(result.responseBody()).contains(orderId.toString());
        verifyNoInteractions(orderTransactionalService);
    }

    @Test
    @DisplayName("placeOrder — delegates to transactional service on new key")
    void placeOrder_newKey_delegatesToTransactionalService() {
        when(idempotencyKeyRepository.findById("new-key"))
                .thenReturn(Optional.empty());

        IdempotencyResult expected = IdempotencyResult.fresh(201, "{\"id\":\"test\"}");
        when(orderTransactionalService.placeOrderTransactional(any(), any(), any()))
                .thenReturn(expected);

        IdempotencyResult result = orderService.placeOrder(
                "new-key", customerId, null
        );

        assertThat(result.isReplay()).isFalse();
        assertThat(result.httpStatus()).isEqualTo(201);
        verify(orderTransactionalService)
                .placeOrderTransactional("new-key", customerId, null);
    }

    // ── updateStatus ───────────────────────────────────────────────────────

    @Test
    @DisplayName("updateStatus — valid transition saves order and outbox event")
    void updateStatus_validTransition_savesOrderAndOutboxEvent() throws Exception {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(pendingOrder));
        when(orderRepository.save(any()))
                .thenReturn(pendingOrder);
        when(orderMapper.toOrderStatusChangedEvent(any(), any(), any()))
                .thenReturn(new OrderStatusChangedEvent(
                        UUID.randomUUID().toString(),
                        orderId.toString(),
                        customerId.toString(),
                        "PENDING", "CONFIRMED",
                        Instant.now().toString()
                ));
        when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        when(outboxRepository.save(any())).thenReturn(mock(OutboxEvent.class));
        when(orderMapper.toOrderResponse(any())).thenReturn(mockResponse);

        OrderResponse response = orderService.updateStatus(
                orderId, customerId, new UpdateOrderStatusRequest("CONFIRMED")
        );

        assertThat(response).isNotNull();
        verify(orderRepository).save(any());
        verify(outboxRepository).save(any());
    }

    @Test
    @DisplayName("updateStatus — duplicate request returns 200 without any DB write")
    void updateStatus_duplicateRequest_noDbWrite() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(confirmedOrder));
        when(orderMapper.toOrderResponse(any()))
                .thenReturn(mockResponse);

        orderService.updateStatus(
                orderId, customerId, new UpdateOrderStatusRequest("CONFIRMED")
        );

        verify(orderRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("updateStatus — throws OrderNotFoundException when order does not exist")
    void updateStatus_orderNotFound_throwsException() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                orderService.updateStatus(
                        orderId, customerId, new UpdateOrderStatusRequest("CONFIRMED")
                ))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    @DisplayName("updateStatus — throws InvalidStatusTransitionException for invalid transition")
    void updateStatus_invalidTransition_throwsException() {
        Order deliveredOrder = Order.builder()
                .id(orderId)
                .customerId(customerId)
                .status(OrderStatus.DELIVERED)
                .totalAmount(new BigDecimal("30.97"))
                .items(new ArrayList<>())
                .build();

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(deliveredOrder));

        assertThatThrownBy(() ->
                orderService.updateStatus(
                        orderId, customerId, new UpdateOrderStatusRequest("CANCELLED")
                ))
                .isInstanceOf(InvalidStatusTransitionException.class);
    }

    @Test
    @DisplayName("updateStatus — throws IllegalArgumentException for unknown status value")
    void updateStatus_unknownStatus_throwsException() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() ->
                orderService.updateStatus(
                        orderId, customerId, new UpdateOrderStatusRequest("FLYING")
                ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("FLYING");
    }

    // ── getOrder ──────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrder — returns order for correct customer")
    void getOrder_correctCustomer_returnsOrder() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(pendingOrder));
        when(orderMapper.toOrderResponse(pendingOrder))
                .thenReturn(mockResponse);

        OrderResponse response = orderService.getOrder(orderId, customerId);

        assertThat(response).isNotNull();
        assertThat(response.id()).isEqualTo(orderId);
    }

    @Test
    @DisplayName("getOrder — throws OrderNotFoundException when order does not exist")
    void getOrder_orderNotFound_throwsException() {
        when(orderRepository.findById(orderId))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(orderId, customerId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(orderId.toString());
    }

    @Test
    @DisplayName("getOrder — throws OrderOwnershipException for wrong customer")
    void getOrder_wrongCustomer_throwsException() {
        UUID wrongCustomerId = UUID.randomUUID();

        when(orderRepository.findById(orderId))
                .thenReturn(Optional.of(pendingOrder));

        assertThatThrownBy(() -> orderService.getOrder(orderId, wrongCustomerId))
                .isInstanceOf(OrderOwnershipException.class);
    }
}