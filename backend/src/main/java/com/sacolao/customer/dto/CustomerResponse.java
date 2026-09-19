package com.sacolao.customer.dto;

import java.time.Instant;
import java.util.UUID;

public record CustomerResponse(
        UUID id,
        UUID establishmentId,
        String name,
        String phone,
        String email,
        Instant createdAt
) {
}
