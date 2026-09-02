package com.sacolao.security;

import com.sacolao.user.entity.UserRole;

import java.util.UUID;

public record AuthenticatedUser(
        UUID id,
        String email,
        UserRole role,
        UUID establishmentId
) {
}
