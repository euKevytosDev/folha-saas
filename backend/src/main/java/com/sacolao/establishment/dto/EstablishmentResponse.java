package com.sacolao.establishment.dto;

import com.sacolao.establishment.entity.PlanCode;

import java.time.Instant;
import java.util.UUID;

public record EstablishmentResponse(
        UUID id,
        String name,
        String slug,
        String logoUrl,
        String description,
        String phone,
        String email,
        String address,
        String city,
        String state,
        String zipCode,
        PlanCode planCode,
        boolean active,
        Instant createdAt,
        Instant updatedAt
) {
}
