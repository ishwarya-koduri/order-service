package com.fooddelivery.orderservice.repository;

import com.fooddelivery.orderservice.model.Order;
import com.fooddelivery.orderservice.model.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.UUID;

/**
 * Repository for Order entity.
 * Uses JOIN FETCH to avoid N+1 problem when loading orders with items.
 */
@Repository
public interface OrderJpaRepository extends JpaRepository<Order, UUID> {

    @Query(
            value = """
                SELECT DISTINCT o FROM Order o
                LEFT JOIN FETCH o.items
                WHERE (:customerId IS NULL OR o.customerId = :customerId)
                AND   (:status IS NULL OR o.status = :status)
                ORDER BY o.createdAt DESC
                """,
            countQuery = """
                SELECT COUNT(DISTINCT o) FROM Order o
                WHERE (:customerId IS NULL OR o.customerId = :customerId)
                AND   (:status IS NULL OR o.status = :status)
                """
    )
    Page<Order> findByCustomerIdAndStatus(
            @Param("customerId") UUID customerId,
            @Param("status") OrderStatus status,
            Pageable pageable
    );
}