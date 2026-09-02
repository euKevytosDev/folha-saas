package com.sacolao.user.dto;

import com.sacolao.user.entity.UserRole;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(
        @Size(min = 2, max = 160) String name,
        UserRole role,
        Boolean active
) {
}
