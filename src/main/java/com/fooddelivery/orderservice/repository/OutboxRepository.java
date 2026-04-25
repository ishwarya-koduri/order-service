package com.fooddelivery.orderservice.repository;

import com.fooddelivery.orderservice.model.OutboxEvent;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // ──────────────────────────────────────────────────────────────────────
    // The core poller query.
    //
    // FOR UPDATE SKIP LOCKED:
    //   - FOR UPDATE     → acquires a row-level lock on each selected row
    //   - SKIP LOCKED    → if another poller instance has already locked a
    //                      row, skip it instead of waiting
    //
    // This makes the poller safe to run on multiple instances simultaneously:
    //   - Instance 1 locks rows 1-50 and publishes them
    //   - Instance 2 skips rows 1-50 and picks up rows 51-100
    //   - Zero duplicate publishing
    //
    // ORDER BY created_at ASC → events published in the order they were created.
    //   Older events always published before newer ones.
    //
    // Pageable → limits the batch size (e.g. LIMIT 50 per poll cycle).
    //   Prevents the poller from trying to publish millions of events at once
    //   after a long Kafka outage.
    // ──────────────────────────────────────────────────────────────────────
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM OutboxEvent e
            WHERE e.published = false
            ORDER BY e.createdAt ASC
            """)
    List<OutboxEvent> findUnpublishedBatch(Pageable pageable);
}