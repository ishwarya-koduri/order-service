package com.fooddelivery.orderservice.model;

import com.fooddelivery.orderservice.config.ApplicationLogger;
import com.fooddelivery.orderservice.exception.InvalidStatusTransitionException;
import com.fooddelivery.orderservice.exception.OrderOwnershipException;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

/**
 * Aggregate root for the order lifecycle.
 * All state changes go through this class.
 */
@Entity
@Table(name = "orders")
@Getter
@Builder
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
@AllArgsConstructor(access = lombok.AccessLevel.PRIVATE)
public class Order extends AuditEntity {

    private static final ApplicationLogger log = ApplicationLogger.getLogger(Order.class);

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "customer_id", nullable = false, updatable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    // Optimistic locking — prevents concurrent updates corrupting order state
    @Version
    @Column(name = "version", nullable = false)
    private Integer version;

    @Builder.Default
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private List<OrderItem> items = new ArrayList<>();

    /**
     * Transitions order to a new status.
     * Returns silently if already in the requested status — handles upstream retries.
     * Throws InvalidStatusTransitionException if the transition is not allowed.
     */
    public void transitionTo(OrderStatus newStatus) {
        if (this.status == newStatus) {
            log.info("Order {} already in status {} — ignoring duplicate", id, newStatus);
            return;
        }
        if (!this.status.canTransitionTo(newStatus)) {
            throw new InvalidStatusTransitionException(id, this.status, newStatus);
        }
        log.info("Order {} status {} -> {}", id, this.status, newStatus);
        this.status = newStatus;
    }

    /**
     * Verifies the requesting customer owns this order.
     * Throws OrderOwnershipException if they do not.
     */
    public void verifyOwnership(UUID requestingCustomerId) {
        if (!this.customerId.equals(requestingCustomerId)) {
            throw new OrderOwnershipException(id, requestingCustomerId);
        }
    }

    /** Returns an unmodifiable view of order items */
    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }
}