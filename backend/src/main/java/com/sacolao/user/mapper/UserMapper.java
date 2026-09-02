package com.sacolao.user.mapper;

import com.sacolao.user.dto.UserResponse;
import com.sacolao.user.entity.User;

public final class UserMapper {

    private UserMapper() {
    }

    public static UserResponse toResponse(User user) {
        return new UserResponse(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getEstablishmentId(),
                user.isActive(),
                user.getCreatedAt()
        );
    }
}
