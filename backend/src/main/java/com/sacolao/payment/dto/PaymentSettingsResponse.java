package com.sacolao.payment.dto;

import com.sacolao.payment.entity.PaymentProviderType;
import com.fasterxml.jackson.annotation.JsonProperty;

public record PaymentSettingsResponse(
        PaymentProviderType provider,
        @JsonProperty("accessTokenConfigured") boolean hasAccessToken,
        boolean pixEnabled,
        boolean onlineEnabled,
        boolean mockMode
) {
}
