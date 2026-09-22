package com.sacolao.store.dto;

public record PublicPaymentOptionsResponse(
        boolean pixEnabled,
        boolean cashEnabled,
        boolean cardEnabled,
        boolean onDeliveryEnabled
) {
}
