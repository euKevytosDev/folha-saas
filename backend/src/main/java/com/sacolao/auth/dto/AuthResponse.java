package com.sacolao.auth.dto;

import com.sacolao.establishment.dto.EstablishmentResponse;
import com.sacolao.user.dto.UserResponse;

public record AuthResponse(
        String accessToken,
        String tokenType,
        long expiresIn,
        String refreshToken,
        UserResponse user,
        EstablishmentResponse establishment
) {
}
