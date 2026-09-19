package com.sacolao.payment.mapper;

import com.sacolao.payment.dto.PaymentResponse;
import com.sacolao.payment.dto.PaymentSettingsResponse;
import com.sacolao.payment.entity.EstablishmentPaymentSettings;
import com.sacolao.payment.entity.Payment;

public final class PaymentMapper {

    private PaymentMapper() {
    }

    public static PaymentResponse toResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrder().getId(),
                payment.getProvider(),
                payment.getStatus(),
                payment.getMethod(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getExternalId(),
                payment.getPixCopyPaste(),
                payment.getPixQrCodeBase64(),
                payment.getCheckoutUrl(),
                payment.getPaidAt(),
                payment.getCreatedAt()
        );
    }

    public static PaymentSettingsResponse toSettingsResponse(EstablishmentPaymentSettings settings) {
        return new PaymentSettingsResponse(
                settings.getProvider(),
                settings.getAccessToken() != null && !settings.getAccessToken().isBlank(),
                settings.isPixEnabled(),
                settings.isOnlineEnabled(),
                settings.isMockMode()
        );
    }
}
