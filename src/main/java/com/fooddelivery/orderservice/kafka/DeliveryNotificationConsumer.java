package com.fooddelivery.orderservice.kafka;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fooddelivery.orderservice.config.ApplicationLogger;
import com.fooddelivery.orderservice.model.DeliveryNotificationEntity;
import com.fooddelivery.orderservice.repository.DeliveryNotificationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Consumes order status change events from Kafka and persists
 * a delivery notification record for each event received.
 */
@Component
@RequiredArgsConstructor
public class DeliveryNotificationConsumer {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(DeliveryNotificationConsumer.class);

    private final DeliveryNotificationRepository deliveryNotificationRepository;
    private final ObjectMapper objectMapper;

    /**
     * Listens to order-status-changed topic and persists a delivery notification.
     * Duplicate events are handled via UNIQUE constraint on event_id —
     * if the same event arrives twice, the second insert is silently ignored.
     */
    @KafkaListener(
            topics = "${app.kafka.topic.order-status-changed}",
            groupId = "delivery-notification-group"
    )
    public void consume(String message) {
        log.info("consume started — message received");

        OrderStatusChangedEvent event = deserialize(message);

        if (event == null) {
            log.error("consume failed — could not deserialize message: {}", message);
            return;
        }

        log.info("consume — eventId={} orderId={} {} -> {}",
                event.eventId(), event.orderId(),
                event.previousStatus(), event.newStatus());

        try {
            DeliveryNotificationEntity notification = DeliveryNotificationEntity.builder()
                    .id(UUID.randomUUID())
                    .orderId(UUID.fromString(event.orderId()))
                    .customerId(UUID.fromString(event.customerId()))
                    .eventId(event.eventId())
                    .previousStatus(event.previousStatus())
                    .newStatus(event.newStatus())
                    .build();

            deliveryNotificationRepository.save(notification);

            log.info("consume completed — eventId={} orderId={} persisted successfully",
                    event.eventId(), event.orderId());

        } catch (DataIntegrityViolationException e) {
            // UNIQUE constraint on event_id rejected the duplicate — safe to ignore
            log.warn("consume — duplicate event received, ignoring eventId={}", event.eventId());
        }
    }

    private OrderStatusChangedEvent deserialize(String message) {
        try {
            return objectMapper.readValue(message, OrderStatusChangedEvent.class);
        } catch (JsonProcessingException e) {
            log.error("deserialize failed — invalid JSON message: {}", message, e);
            return null;
        }
    }
}