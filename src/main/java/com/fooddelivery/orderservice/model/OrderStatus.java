package com.fooddelivery.orderservice.model;

import java.util.Map;
import java.util.Set;

/**
 * Represents the lifecycle states of an order and enforces valid transitions.
 */
public enum OrderStatus {

    PENDING,
    CONFIRMED,
    PREPARING,
    OUT_FOR_DELIVERY,
    DELIVERED,
    CANCELLED;

    // Defines which statuses each state can move to
    private static final Map<OrderStatus, Set<OrderStatus>> VALID_TRANSITIONS = Map.of(
            PENDING,          Set.of(CONFIRMED, CANCELLED),
            CONFIRMED,        Set.of(PREPARING, CANCELLED),
            PREPARING,        Set.of(OUT_FOR_DELIVERY),
            OUT_FOR_DELIVERY, Set.of(DELIVERED),
            DELIVERED,        Set.of(),
            CANCELLED,        Set.of()
    );

    /** Returns true if transitioning to the given status is allowed */
    public boolean canTransitionTo(OrderStatus next) {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).contains(next);
    }

    /** Returns true if this status has no further transitions (DELIVERED or CANCELLED) */
    public boolean isTerminal() {
        return VALID_TRANSITIONS.getOrDefault(this, Set.of()).isEmpty();
    }
}