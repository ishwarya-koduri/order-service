package com.fooddelivery.orderservice.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

// ──────────────────────────────────────────────────────────────────────────
// JPA entity for the outbox_events table.
//
// An OutboxEvent is written IN THE SAME TRANSACTION as the order mutation.
// This guarantees that if the order is saved, the event will eventually
// be published to Kafka — even if the service crashes immediately after.
//
// The OutboxPoller reads rows WHERE published = false and publishes them.
// ──────────────────────────────────────────────────────────────────────────
@Entity
@Table(name = "outbox_events")
@Getter
@Builder
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class OutboxEvent extends AuditEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "order_id", nullable = false, updatable = false)
    private UUID orderId;

    @Column(name = "event_type", nullable = false, updatable = false, length = 100)
    private String eventType;

    @Column(name = "payload", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String payload;

    @Column(name = "published", nullable = false)
    private boolean published;

    @Column(name = "published_at")
    private Instant publishedAt;

    /** Marks this event as successfully published to Kafka */
    public void markPublished() {
        this.published   = true;
        this.publishedAt = Instant.now();
    }
}