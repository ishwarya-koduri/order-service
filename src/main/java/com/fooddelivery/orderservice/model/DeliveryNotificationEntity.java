package com.fooddelivery.orderservice.model;

import com.fooddelivery.orderservice.kafka.OrderStatusChangedEvent;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Persists a record for every order status change event consumed from Kafka.
 * event_id has a UNIQUE constraint — prevents duplicate records from
 * at-least-once Kafka delivery.
 */
@Entity
@Table(name = "delivery_notifications")
@Getter
@Builder
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class DeliveryNotificationEntity extends AuditEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    // Unique constraint defined in V1 migration — deduplication key
    @Column(name = "event_id", nullable = false, updatable = false, unique = true)
    private String eventId;

    @Column(name = "previous_status", updatable = false, length = 30)
    private String previousStatus;

    @Column(name = "new_status", nullable = false, updatable = false, length = 30)
    private String newStatus;
}