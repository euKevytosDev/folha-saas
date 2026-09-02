package com.sacolao.auth.dto;

import com.sacolao.establishment.dto.EstablishmentResponse;
import com.sacolao.user.dto.UserResponse;

public record MeResponse(
        UserResponse user,
        EstablishmentResponse establishment
) {
}
