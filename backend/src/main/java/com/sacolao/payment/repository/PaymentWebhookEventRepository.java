package com.sacolao.payment.repository;

import com.sacolao.payment.entity.PaymentProviderType;
import com.sacolao.payment.entity.PaymentWebhookEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEvent, UUID> {

    Optional<PaymentWebhookEvent> findByProviderAndProviderEventId(PaymentProviderType provider, String providerEventId);
}
