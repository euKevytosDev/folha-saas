package com.sacolao.payment.repository;

import com.sacolao.payment.entity.IdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IdempotencyKeyRepository extends JpaRepository<IdempotencyKey, UUID> {

    Optional<IdempotencyKey> findByEstablishment_IdAndScopeAndKeyHash(UUID establishmentId, String scope, String keyHash);
}
