package com.sacolao.payment.gateway;

import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.payment.entity.PaymentProviderType;
import com.sacolao.payment.entity.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
public class MockPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.MOCK;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String externalId = "mock-" + UUID.randomUUID();
        if (request.method() == PaymentMethod.PIX) {
            String copyPaste = buildFakePixPayload(request.order().getPublicCode(), request.amount().toPlainString());
            return new ChargeResult(
                    PaymentStatus.PENDING,
                    externalId,
                    copyPaste,
                    null,
                    null,
                    "{\"provider\":\"MOCK\",\"status\":\"PENDING\"}"
            );
        }
        if (request.method() == PaymentMethod.CARD && request.settings().isOnlineEnabled()) {
            return new ChargeResult(
                    PaymentStatus.PENDING,
                    externalId,
                    null,
                    null,
                    null,
                    "{\"provider\":\"MOCK\",\"status\":\"PENDING\",\"method\":\"CARD\"}"
            );
        }
        return ChargeResult.pendingManual(externalId);
    }

    private static String buildFakePixPayload(String publicCode, String amount) {
        String normalized = publicCode == null ? "FOLHA" : publicCode.toUpperCase(Locale.ROOT);
        return "00020126580014br.gov.bcb.pix0136folha-mock-" + normalized
                + "520400005303986540" + String.format("%02d", Math.min(amount.length(), 99)) + amount
                + "5802BR5913Folha Mock PIX6009SAO PAULO62070503***6304ABCD";
    }
}
