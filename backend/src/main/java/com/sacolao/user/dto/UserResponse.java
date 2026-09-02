package com.sacolao.user.dto;

import com.sacolao.user.entity.UserRole;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
        UUID id,
        String name,
        String email,
        UserRole role,
        UUID establishmentId,
        boolean active,
        Instant createdAt
) {
}
