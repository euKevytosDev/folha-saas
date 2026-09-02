package com.sacolao.user.entity;

public enum UserRole {
    SUPER_ADMIN,
    OWNER,
    ADMIN,
    STAFF;

    public boolean isTenantBound() {
        return this != SUPER_ADMIN;
    }
}
