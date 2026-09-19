package com.sacolao.payment.gateway;

import com.sacolao.order.entity.Order;
import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.payment.entity.EstablishmentPaymentSettings;
import com.sacolao.payment.entity.PaymentProviderType;
import com.sacolao.payment.entity.PaymentStatus;

import java.math.BigDecimal;

public interface PaymentGateway {

    PaymentProviderType provider();

    ChargeResult charge(ChargeRequest request);

    record ChargeRequest(
            Order order,
            EstablishmentPaymentSettings settings,
            PaymentMethod method,
            BigDecimal amount,
            String idempotencyKey
    ) {
    }

    record ChargeResult(
            PaymentStatus status,
            String externalId,
            String pixCopyPaste,
            String pixQrCodeBase64,
            String checkoutUrl,
            String rawResponse
    ) {
        public static ChargeResult pendingManual(String externalId) {
            return new ChargeResult(PaymentStatus.PENDING, externalId, null, null, null, null);
        }

        public static ChargeResult paidManual(String externalId) {
            return new ChargeResult(PaymentStatus.PAID, externalId, null, null, null, null);
        }
    }
}
