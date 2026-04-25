package com.fooddelivery.orderservice.repository;

import com.fooddelivery.orderservice.model.IdempotencyKeyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// ──────────────────────────────────────────────────────────────────────────
// Simple Spring Data repository for idempotency key lookup and storage.
// Primary key is String (the idempotency key value itself).
//
// findById() from JpaRepository is sufficient for the lookup.
// save() from JpaRepository handles the insert.
// ──────────────────────────────────────────────────────────────────────────
@Repository
public interface IdempotencyKeyRepository
        extends JpaRepository<IdempotencyKeyEntity, String> {

    // findById(String idempotencyKey) is inherited from JpaRepository.
    // Returns Optional<IdempotencyKeyEntity> — empty if key not seen before.

    // save(IdempotencyKeyEntity entity) is inherited from JpaRepository.
    // Inserts the record. If the key already exists, this will throw
    // DataIntegrityViolationException — which we handle at the call site.
}