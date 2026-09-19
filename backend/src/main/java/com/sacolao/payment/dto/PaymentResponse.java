package com.sacolao.payment.dto;

import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.payment.entity.PaymentProviderType;
import com.sacolao.payment.entity.PaymentStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record PaymentResponse(
        UUID id,
        UUID orderId,
        PaymentProviderType provider,
        PaymentStatus status,
        PaymentMethod method,
        BigDecimal amount,
        String currency,
        String externalId,
        String pixCopyPaste,
        String pixQrCodeBase64,
        String checkoutUrl,
        Instant paidAt,
        Instant createdAt
) {
}
