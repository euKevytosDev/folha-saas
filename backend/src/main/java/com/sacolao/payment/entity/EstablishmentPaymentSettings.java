package com.sacolao.payment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "establishment_payment_settings")
public class EstablishmentPaymentSettings {

    @Id
    @Column(name = "establishment_id")
    private UUID establishmentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private PaymentProviderType provider = PaymentProviderType.MERCADO_PAGO;

    @Column(name = "access_token", columnDefinition = "text")
    private String accessToken;

    @Column(name = "webhook_secret", length = 255)
    private String webhookSecret;

    @Column(name = "pix_enabled", nullable = false)
    private boolean pixEnabled = true;

    @Column(name = "pay_on_delivery_only", nullable = false)
    private boolean payOnDeliveryOnly;

    @Column(name = "online_enabled", nullable = false)
    private boolean onlineEnabled;

    @Column(name = "mock_mode", nullable = false)
    private boolean mockMode = true;

    @Column(nullable = false)
    private boolean sandbox;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    void onCreate() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = Instant.now();
    }

    public UUID getEstablishmentId() {
        return establishmentId;
    }

    public void setEstablishmentId(UUID establishmentId) {
        this.establishmentId = establishmentId;
    }

    public PaymentProviderType getProvider() {
        return provider;
    }

    public void setProvider(PaymentProviderType provider) {
        this.provider = provider;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public void setAccessToken(String accessToken) {
        this.accessToken = accessToken;
    }

    public String getWebhookSecret() {
        return webhookSecret;
    }

    public void setWebhookSecret(String webhookSecret) {
        this.webhookSecret = webhookSecret;
    }

    public boolean isPixEnabled() {
        return pixEnabled;
    }

    public void setPixEnabled(boolean pixEnabled) {
        this.pixEnabled = pixEnabled;
    }

    public boolean isPayOnDeliveryOnly() {
        return payOnDeliveryOnly;
    }

    public void setPayOnDeliveryOnly(boolean payOnDeliveryOnly) {
        this.payOnDeliveryOnly = payOnDeliveryOnly;
    }

    public boolean isOnlineEnabled() {
        return onlineEnabled;
    }

    public void setOnlineEnabled(boolean onlineEnabled) {
        this.onlineEnabled = onlineEnabled;
    }

    public boolean isMockMode() {
        return mockMode;
    }

    public void setMockMode(boolean mockMode) {
        this.mockMode = mockMode;
    }

    public boolean isSandbox() {
        return sandbox;
    }

    public void setSandbox(boolean sandbox) {
        this.sandbox = sandbox;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
