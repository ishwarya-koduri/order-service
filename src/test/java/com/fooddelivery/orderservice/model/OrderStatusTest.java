package com.fooddelivery.orderservice.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus — State Machine")
class OrderStatusTest {

    @ParameterizedTest(name = "{0} -> {1} should be allowed")
    @CsvSource({
            "PENDING,          CONFIRMED",
            "PENDING,          CANCELLED",
            "CONFIRMED,        PREPARING",
            "CONFIRMED,        CANCELLED",
            "PREPARING,        OUT_FOR_DELIVERY",
            "OUT_FOR_DELIVERY, DELIVERED"
    })
    @DisplayName("Valid transitions return true")
    void validTransitions(String from, String to) {
        OrderStatus current = OrderStatus.valueOf(from.trim());
        OrderStatus next    = OrderStatus.valueOf(to.trim());

        assertThat(current.canTransitionTo(next)).isTrue();
    }

    @ParameterizedTest(name = "{0} -> {1} should be rejected")
    @CsvSource({
            "PENDING,          PREPARING",
            "PENDING,          OUT_FOR_DELIVERY",
            "PENDING,          DELIVERED",
            "CONFIRMED,        PENDING",
            "CONFIRMED,        DELIVERED",
            "CONFIRMED,        OUT_FOR_DELIVERY",
            "PREPARING,        CONFIRMED",
            "PREPARING,        CANCELLED",
            "PREPARING,        PENDING",
            "OUT_FOR_DELIVERY, PENDING",
            "OUT_FOR_DELIVERY, CONFIRMED",
            "OUT_FOR_DELIVERY, CANCELLED",
            "DELIVERED,        CANCELLED",
            "DELIVERED,        PENDING",
            "DELIVERED,        CONFIRMED",
            "CANCELLED,        PENDING",
            "CANCELLED,        CONFIRMED",
            "CANCELLED,        PREPARING"
    })
    @DisplayName("Invalid transitions return false")
    void invalidTransitions(String from, String to) {
        OrderStatus current = OrderStatus.valueOf(from.trim());
        OrderStatus next    = OrderStatus.valueOf(to.trim());

        assertThat(current.canTransitionTo(next)).isFalse();
    }

    @Test
    @DisplayName("DELIVERED is terminal")
    void delivered_isTerminal() {
        assertThat(OrderStatus.DELIVERED.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("CANCELLED is terminal")
    void cancelled_isTerminal() {
        assertThat(OrderStatus.CANCELLED.isTerminal()).isTrue();
    }

    @ParameterizedTest(name = "{0} is not terminal")
    @CsvSource({"PENDING", "CONFIRMED", "PREPARING", "OUT_FOR_DELIVERY"})
    @DisplayName("Active statuses are not terminal")
    void activeStatuses_areNotTerminal(String status) {
        assertThat(OrderStatus.valueOf(status).isTerminal()).isFalse();
    }

    @ParameterizedTest(name = "{0} -> {0} same status returns false")
    @CsvSource({"PENDING", "CONFIRMED", "PREPARING", "OUT_FOR_DELIVERY"})
    @DisplayName("Same status transition returns false — handled as idempotent at Order level")
    void sameStatusTransition_returnsFalse(String status) {
        OrderStatus current = OrderStatus.valueOf(status);
        assertThat(current.canTransitionTo(current)).isFalse();
    }
}