package com.fooddelivery.orderservice.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.orderservice.config.ApplicationLogger;
import com.fooddelivery.orderservice.dto.CreateOrderRequest;
import com.fooddelivery.orderservice.dto.IdempotencyResult;
import com.fooddelivery.orderservice.dto.OrderResponse;
import com.fooddelivery.orderservice.exception.EventSerializationException;
import com.fooddelivery.orderservice.exception.OrderPersistenceException;
import com.fooddelivery.orderservice.kafka.OrderStatusChangedEvent;
import com.fooddelivery.orderservice.mapper.OrderMapper;
import com.fooddelivery.orderservice.model.IdempotencyKeyEntity;
import com.fooddelivery.orderservice.model.Order;
import com.fooddelivery.orderservice.model.OrderItem;
import com.fooddelivery.orderservice.model.OrderStatus;
import com.fooddelivery.orderservice.model.OutboxEvent;
import com.fooddelivery.orderservice.repository.IdempotencyKeyRepository;
import com.fooddelivery.orderservice.repository.OrderJpaRepository;
import com.fooddelivery.orderservice.repository.OutboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Handles the transactional part of order placement.
 * Separate bean from OrderService so @Transactional proxy works correctly.
 */
@Service
@RequiredArgsConstructor
public class OrderTransactionalService {

    private static final ApplicationLogger log =
            ApplicationLogger.getLogger(OrderTransactionalService.class);

    private final OrderJpaRepository orderRepository;
    private final IdempotencyKeyRepository idempotencyKeyRepository;
    private final OutboxRepository outboxRepository;
    private final OrderMapper orderMapper;
    private final ObjectMapper objectMapper;

    /**
     * Orchestrates order placement in a single transaction.
     * Delegates each save to a dedicated private method.
     * All three writes commit together or roll back together.
     */
    @Transactional
    public IdempotencyResult placeOrderTransactional(
            String idempotencyKey,
            UUID customerId,
            CreateOrderRequest request
    ) {
        log.info("placeOrderTransactional started — customerId={} idempotencyKey={}",
                customerId, idempotencyKey);

        registerRollbackLogger(customerId, idempotencyKey);

        Order order = buildOrder(customerId, request);

        persistOrder(order);
        persistOutboxEvent(order);
        IdempotencyResult result = persistIdempotencyKey(idempotencyKey, order);

        log.info("placeOrderTransactional completed — orderId={} idempotencyKey={}",
                order.getId(), idempotencyKey);

        return result;
    }

    /**
     * Persists the order and its items to the database.
     */
    private void persistOrder(Order order) {
        log.debug("persistOrder started — orderId={}", order.getId());
        try {
            orderRepository.save(order);
            log.info("persistOrder completed — orderId={} totalAmount={} itemCount={}",
                    order.getId(), order.getTotalAmount(), order.getItems().size());

        } catch (DataIntegrityViolationException e) {
            log.error("persistOrder failed — constraint violation orderId={}", order.getId(), e);
            throw new OrderPersistenceException(
                    "Failed to persist order due to a conflict. Please retry.", e);

        } catch (DataAccessException e) {
            log.error("persistOrder failed — DB error orderId={}", order.getId(), e);
            throw new OrderPersistenceException(
                    "Failed to persist order due to a database error. Please retry.", e);

        } catch (Exception e) {
            log.error("persistOrder failed — unexpected error orderId={}", order.getId(), e);
            throw new OrderPersistenceException(
                    "Order placement failed unexpectedly. Please retry.", e);
        }
    }

    /**
     * Writes the ORDER_PLACED outbox event in the same transaction as the order.
     * If Kafka is down, this row stays unpublished and OutboxPoller retries it.
     */
    private void persistOutboxEvent(Order order) {
        log.debug("persistOutboxEvent started — orderId={}", order.getId());
        try {
            OrderStatusChangedEvent event = orderMapper.toOrderStatusChangedEvent(
                    order, null, OrderStatus.PENDING
            );

            outboxRepository.save(
                    OutboxEvent.builder()
                            .id(UUID.randomUUID())
                            .orderId(order.getId())
                            .eventType("ORDER_PLACED")
                            .payload(serialize(event))
                            .published(false)
                            .build()
            );

            log.debug("persistOutboxEvent completed — orderId={}", order.getId());

        } catch (EventSerializationException e) {
            log.error("persistOutboxEvent failed — serialization error orderId={}",
                    order.getId(), e);
            throw e;

        } catch (DataAccessException e) {
            log.error("persistOutboxEvent failed — DB error orderId={}", order.getId(), e);
            throw new OrderPersistenceException(
                    "Failed to persist order event. Please retry.", e);

        } catch (Exception e) {
            log.error("persistOutboxEvent failed — unexpected error orderId={}",
                    order.getId(), e);
            throw new OrderPersistenceException(
                    "Order placement failed unexpectedly. Please retry.", e);
        }
    }

    /**
     * Stores the idempotency key with the exact HTTP response for future replays.
     * Handles race condition where two concurrent requests try to insert the same key.
     */
    private IdempotencyResult persistIdempotencyKey(String idempotencyKey, Order order) {
        log.debug("persistIdempotencyKey started — key={} orderId={}",
                idempotencyKey, order.getId());
        try {
            OrderResponse response   = orderMapper.toOrderResponse(order);
            String        responseJson = serialize(response);

            idempotencyKeyRepository.save(
                    IdempotencyKeyEntity.builder()
                            .idempotencyKey(idempotencyKey)
                            .orderId(order.getId())
                            .httpStatus(HttpStatus.CREATED.value())
                            .responseBody(responseJson)
                            .build()
            );

            log.debug("persistIdempotencyKey completed — key={} orderId={}",
                    idempotencyKey, order.getId());

            return IdempotencyResult.fresh(HttpStatus.CREATED.value(), responseJson);

        } catch (DataIntegrityViolationException e) {
            // Race condition — concurrent request already committed the same key
            log.warn("persistIdempotencyKey — duplicate key under concurrent request " +
                    "key={} orderId={}", idempotencyKey, order.getId());

            return idempotencyKeyRepository.findById(idempotencyKey)
                    .map(existing -> IdempotencyResult.replay(
                            existing.getHttpStatus(),
                            existing.getResponseBody()))
                    .orElseThrow(() -> new OrderPersistenceException(
                            "Concurrent request conflict — please retry.", e));

        } catch (DataAccessException e) {
            log.error("persistIdempotencyKey failed — DB error key={} orderId={}",
                    idempotencyKey, order.getId(), e);
            throw new OrderPersistenceException(
                    "Failed to store request key. Please retry.", e);

        } catch (Exception e) {
            log.error("persistIdempotencyKey failed — unexpected error key={} orderId={}",
                    idempotencyKey, order.getId(), e);
            throw new OrderPersistenceException(
                    "Order placement failed unexpectedly. Please retry.", e);
        }
    }

    /** Builds the Order entity from the incoming request */
    private Order buildOrder(UUID customerId, CreateOrderRequest request) {
        log.debug("buildOrder started — customerId={} itemCount={}",
                customerId, request.items().size());

        List<OrderItem> items = request.items().stream()
                .map(i -> OrderItem.builder()
                        .id(UUID.randomUUID())
                        .menuItemId(i.menuItemId())
                        .name(i.name())
                        .quantity(i.quantity())
                        .unitPrice(i.unitPrice())
                        .build())
                .toList();

        BigDecimal total = items.stream()
                .map(OrderItem::subtotal)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        Order order = Order.builder()
                .id(UUID.randomUUID())
                .customerId(customerId)
                .status(OrderStatus.PENDING)
                .totalAmount(total)
                .items(new ArrayList<>(items))
                .build();

        log.debug("buildOrder completed — orderId={} totalAmount={}",
                order.getId(), total);

        return order;
    }

    /** Registers a callback that logs explicitly when the transaction rolls back */
    private void registerRollbackLogger(UUID customerId, String idempotencyKey) {
        TransactionSynchronizationManager.registerSynchronization(
                new TransactionSynchronization() {
                    @Override
                    public void afterCompletion(int status) {
                        if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                            log.error(
                                    "Transaction ROLLED BACK — all writes undone " +
                                            "customerId={} idempotencyKey={}",
                                    customerId, idempotencyKey
                            );
                        }
                    }
                }
        );
    }

    private String serialize(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            throw new EventSerializationException(
                    "Failed to serialize object of type: "
                            + obj.getClass().getSimpleName(), e);
        }
    }
}