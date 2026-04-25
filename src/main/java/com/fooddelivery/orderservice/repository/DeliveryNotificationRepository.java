package com.fooddelivery.orderservice.repository;

import com.fooddelivery.orderservice.model.DeliveryNotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface DeliveryNotificationRepository
        extends JpaRepository<DeliveryNotificationEntity, UUID> {
    // save() and findById() from JpaRepository are sufficient.
    // No custom queries needed for the bonus consumer feature.
}