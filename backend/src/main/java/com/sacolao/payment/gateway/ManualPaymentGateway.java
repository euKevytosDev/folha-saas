package com.sacolao.payment.gateway;

import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.payment.entity.PaymentProviderType;
import com.sacolao.payment.entity.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class ManualPaymentGateway implements PaymentGateway {

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.MANUAL;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String externalId = "manual-" + UUID.randomUUID();
        if (request.method() == PaymentMethod.CASH || request.method() == PaymentMethod.ON_DELIVERY) {
            return new ChargeResult(
                    PaymentStatus.PENDING,
                    externalId,
                    null,
                    null,
                    null,
                    "{\"provider\":\"MANUAL\",\"status\":\"PENDING\"}"
            );
        }
        return ChargeResult.pendingManual(externalId);
    }
}
