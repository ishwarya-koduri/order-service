package com.fooddelivery.orderservice.service;

import com.fooddelivery.orderservice.config.ApplicationLogger;
import com.fooddelivery.orderservice.dto.CreateOrderRequest;
import com.fooddelivery.orderservice.dto.IdempotencyResult;
import com.fooddelivery.orderservice.dto.OrderResponse;
import com.fooddelivery.orderservice.dto.UpdateOrderStatusRequest;
import com.fooddelivery.orderservice.exception.EventSerializationException;
import com.fooddelivery.orderservice.exception.OrderNotFoundException;
import com.fooddelivery.orderservice.exception.OrderPersistenceException;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.model.IdempotencyKeyEntity;
import com.fooddelivery.orderservice.model.Order;
import com.fooddelivery.orderservice.model.OrderStatus;
import com.fooddelivery.orderservice.model.OutboxEvent;
import com.fooddelivery.orderservice.kafka.OrderStatusChangedEvent;
import com.fooddelivery.orderservice.repository.IdempotencyKeyRepository;
import com.fooddelivery.orderservice.repository.OrderJpaRepository;
import com.fooddelivery.orderservice.repository.OutboxRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * Handles all order operations — placement, status updates and retrieval.
 */
@Service
@RequiredArgsConstructor
public class OrderService {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(OrderService.class);

    private final OrderJpaRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxRepository outboxRepository;
    private final OrderTransactionalService orderTransactionalService;
    private final ObjectMapper objectMapper;
    private final OrderMapper orderMapper;

    /**
     * Places a new order.
     * Checks idempotency key first — if already processed, returns stored response immediately.
     */
    public IdempotencyResult placeOrder(String idempotencyKey, UUID customerId, CreateOrderRequest request) {
        log.info("placeOrder started — customerId={} idempotencyKey={}", customerId, idempotencyKey);

        Optional<IdempotencyKeyEntity> existing = idempotencyKeyRepository.findById(idempotencyKey);

        if (existing.isPresent()) {
            log.info("placeOrder — idempotency hit for key={} returning stored response", idempotencyKey);
            return IdempotencyResult.replay(
                    existing.get().getHttpStatus(),
                    existing.get().getResponseBody()
            );
        }

        IdempotencyResult result = orderTransactionalService.placeOrderTransactional(
                idempotencyKey, customerId, request
        );

        log.info("placeOrder completed — customerId={} idempotencyKey={} httpStatus={}",
                customerId, idempotencyKey, result.httpStatus());

        return result;
    }

    /**
     * Updates the status of an existing order.
     * Handles duplicate requests safely — returns 200 silently if already in requested status.
     */
    @Transactional
    public OrderResponse updateStatus(UUID orderId, UUID customerId, UpdateOrderStatusRequest request) {
        log.info("updateStatus started — orderId={} customerId={} requestedStatus={}",
                orderId, customerId, request.status());

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.verifyOwnership(customerId);

        OrderStatus previousStatus  = order.getStatus();
        OrderStatus requestedStatus = parseStatus(request.status());

        order.transitionTo(requestedStatus);

        boolean transitioned = !previousStatus.equals(order.getStatus());

        if (transitioned) {
            persistUpdatedOrder(order, previousStatus);
        }

        OrderResponse response = orderMapper.toOrderResponse(order);

        log.info("updateStatus completed — orderId={} previousStatus={} currentStatus={}",
                orderId, previousStatus, order.getStatus());

        return response;
    }

    /** Saves the updated order and its outbox event atomically */
    private void persistUpdatedOrder(Order order, OrderStatus previousStatus) {
        try {
            orderRepository.save(order);
            log.info("Order status persisted — orderId={} status={}",
                    order.getId(), order.getStatus());

        } catch (DataIntegrityViolationException e) {
            log.error("Order status save failed — constraint violation orderId={}",
                    order.getId(), e);
            throw new OrderPersistenceException(
                    "Failed to update order status due to a conflict. Please retry.", e);

        } catch (DataAccessException e) {
            log.error("Order status save failed — DB error orderId={}", order.getId(), e);
            throw new OrderPersistenceException(
                    "Failed to update order status due to a database error. Please retry.", e);

        } catch (Exception e) {
            log.error("Order status save failed — unexpected error orderId={}", order.getId(), e);
            throw new OrderPersistenceException(
                    "Order status update failed unexpectedly. Please retry.", e);
        }

        try {
            saveOutboxEvent(order, previousStatus, order.getStatus());

        } catch (EventSerializationException e) {
            log.error("Outbox event serialization failed — orderId={}", order.getId(), e);
            throw e;

        } catch (DataAccessException e) {
            log.error("Outbox event save failed — orderId={}", order.getId(), e);
            throw new OrderPersistenceException(
                    "Failed to persist status change event. Please retry.", e);

        } catch (Exception e) {
            log.error("Outbox event save failed — unexpected error orderId={}", order.getId(), e);
            throw new OrderPersistenceException(
                    "Order status update failed unexpectedly. Please retry.", e);
        }
    }

    /**
     * Retrieves a single order by ID.
     * Only the owning customer can access their order.
     */
    @Transactional(readOnly = true)
    public OrderResponse getOrder(UUID orderId, UUID customerId) {
        log.info("getOrder started — orderId={} customerId={}", orderId, customerId);

        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        order.verifyOwnership(customerId);

        OrderResponse response = orderMapper.toOrderResponse(order);

        log.info("getOrder completed — orderId={} status={} totalAmount={}",
                orderId, order.getStatus(), order.getTotalAmount());

        return response;
    }

    /**
     * Returns a paginated list of orders filtered by customer and optionally by status.
     */
    @Transactional(readOnly = true)
    public Page<OrderResponse> listOrders(UUID customerId, String status, Pageable pageable) {
        log.info("listOrders started — customerId={} status={} page={} size={}",
                customerId, status, pageable.getPageNumber(), pageable.getPageSize());
        OrderStatus orderStatus = null;
        if (status != null && !status.isBlank()) {
            orderStatus = parseStatus(status);
        }
        Page<OrderResponse> response = orderRepository
                .findByCustomerIdAndStatus(customerId, orderStatus, pageable)
                .map(orderMapper::toOrderResponse);

        log.info("listOrders completed — customerId={} totalResults={}",
                customerId, response.getTotalElements());

        return response;
    }

    private void saveOutboxEvent(Order order, OrderStatus previousStatus, OrderStatus newStatus) {
        log.debug("saveOutboxEvent — orderId={} {} -> {}", order.getId(), previousStatus, newStatus);

        OrderStatusChangedEvent event = orderMapper.toOrderStatusChangedEvent(
                order, previousStatus, newStatus
        );

        outboxRepository.save(
                OutboxEvent.builder()
                        .id(UUID.randomUUID())
                        .orderId(order.getId())
                        .eventType("ORDER_STATUS_CHANGED")
                        .payload(serialize(event))
                        .published(false)
                        .build()
        );

        log.debug("saveOutboxEvent completed — orderId={}", order.getId());
    }

    private OrderStatus parseStatus(String status) {
        try {
            return OrderStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Invalid order status: " + status);
        }
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException("Failed to serialize object", e);
        }
    }
}