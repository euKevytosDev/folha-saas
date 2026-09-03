package com.sacolao.category.dto;

import java.time.Instant;
import java.util.UUID;

public record CategoryResponse(
        UUID id,
        UUID establishmentId,
        String name,
        String description,
        String imageUrl,
        int sortOrder,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
