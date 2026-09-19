package com.sacolao.payment.dto;

import com.sacolao.payment.entity.PaymentProviderType;
import jakarta.validation.constraints.NotNull;

public record UpdatePaymentSettingsRequest(
        @NotNull PaymentProviderType provider,
        String accessToken,
        String webhookSecret,
        Boolean pixEnabled,
        Boolean onlineEnabled,
        Boolean mockMode
) {
}
