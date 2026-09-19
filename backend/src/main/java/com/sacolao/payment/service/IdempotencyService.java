package com.sacolao.payment.service;

import com.sacolao.common.exception.ConflictException;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.payment.entity.IdempotencyKey;
import com.sacolao.payment.repository.IdempotencyKeyRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdempotencyService {

    public static final String SCOPE_CHECKOUT = "checkout";

    private final IdempotencyKeyRepository idempotencyKeyRepository;

    public IdempotencyService(IdempotencyKeyRepository idempotencyKeyRepository) {
        this.idempotencyKeyRepository = idempotencyKeyRepository;
    }

    @Transactional(readOnly = true)
    public Optional<IdempotencyKey> find(UUID establishmentId, String scope, String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            return Optional.empty();
        }
        return idempotencyKeyRepository.findByEstablishment_IdAndScopeAndKeyHash(
                establishmentId,
                scope,
                sha256(rawKey.trim())
        );
    }

    @Transactional
    public IdempotencyKey save(
            Establishment establishment,
            String scope,
            String rawKey,
            String requestPayload,
            String responseBody,
            UUID resourceId,
            int httpStatus
    ) {
        String keyHash = sha256(rawKey.trim());
        String requestHash = sha256(requestPayload == null ? "" : requestPayload);
        Optional<IdempotencyKey> existing = idempotencyKeyRepository
                .findByEstablishment_IdAndScopeAndKeyHash(establishment.getId(), scope, keyHash);
        if (existing.isPresent()) {
            IdempotencyKey found = existing.get();
            if (!found.getRequestHash().equals(requestHash)) {
                throw new ConflictException("IDEMPOTENCY_CONFLICT", "Chave de idempotência reutilizada com payload diferente");
            }
            return found;
        }
        IdempotencyKey key = new IdempotencyKey();
        key.setEstablishment(establishment);
        key.setScope(scope);
        key.setKeyHash(keyHash);
        key.setRequestHash(requestHash);
        key.setResponseBody(responseBody);
        key.setResourceId(resourceId);
        key.setHttpStatus(httpStatus);
        key.setExpiresAt(Instant.now().plus(24, ChronoUnit.HOURS));
        return idempotencyKeyRepository.save(key);
    }

    public static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
