package com.fooddelivery.orderservice.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.UUID;

// ──────────────────────────────────────────────────────────────────────────
// JPA entity for the idempotency_keys table.
// This is purely an infrastructure concern — the domain has no knowledge
// of idempotency storage. It is handled entirely at the application layer.
// ──────────────────────────────────────────────────────────────────────────
@Entity
@Table(name = "idempotency_keys")
@Getter
@Builder
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class IdempotencyKeyEntity extends AuditEntity {

    @Id
    @Column(name = "idempotency_key", nullable = false, updatable = false)
    private String idempotencyKey;

    @Column(name = "order_id", updatable = false)
    private UUID orderId;

    @Column(name = "http_status", nullable = false, updatable = false)
    private Integer httpStatus;

    @Column(name = "response_body", nullable = false,
            updatable = false, columnDefinition = "TEXT")
    private String responseBody;
}