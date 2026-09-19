package com.sacolao.order.entity;

public enum OrderStatus {
    PENDING,
    CONFIRMED,
    PREPARING,
    DISPATCHED,
    DELIVERED,
    CANCELLED;

    public boolean canTransitionTo(OrderStatus next) {
        if (next == null || this == next) {
            return false;
        }
        return switch (this) {
            case PENDING -> next == CONFIRMED || next == CANCELLED;
            case CONFIRMED -> next == PREPARING || next == CANCELLED;
            case PREPARING -> next == DISPATCHED || next == CANCELLED;
            case DISPATCHED -> next == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }

    public boolean isTerminal() {
        return this == DELIVERED || this == CANCELLED;
    }
}
