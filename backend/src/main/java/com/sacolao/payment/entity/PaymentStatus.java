package com.sacolao.payment.entity;

public enum PaymentStatus {
    PENDING,
    AUTHORIZED,
    PAID,
    FAILED,
    REFUNDED,
    CANCELLED,
    EXPIRED;

    public boolean isTerminalSuccess() {
        return this == PAID;
    }

    public boolean isOpen() {
        return this == PENDING || this == AUTHORIZED;
    }
}
