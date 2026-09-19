package com.sacolao.payment.service;

import com.sacolao.common.exception.ResourceNotFoundException;
import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.establishment.entity.Establishment;
import com.sacolao.establishment.repository.EstablishmentRepository;
import com.sacolao.order.entity.Order;
import com.sacolao.order.entity.OrderStatus;
import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.order.repository.OrderRepository;
import com.sacolao.payment.dto.PaymentResponse;
import com.sacolao.payment.dto.PaymentSettingsResponse;
import com.sacolao.payment.dto.UpdatePaymentSettingsRequest;
import com.sacolao.payment.entity.EstablishmentPaymentSettings;
import com.sacolao.payment.entity.Payment;
import com.sacolao.payment.entity.PaymentProviderType;
import com.sacolao.payment.entity.PaymentStatus;
import com.sacolao.payment.entity.PaymentWebhookEvent;
import com.sacolao.payment.gateway.ManualPaymentGateway;
import com.sacolao.payment.gateway.MercadoPagoPaymentGateway;
import com.sacolao.payment.gateway.MockPaymentGateway;
import com.sacolao.payment.gateway.PaymentGateway;
import com.sacolao.payment.mapper.PaymentMapper;
import com.sacolao.payment.repository.EstablishmentPaymentSettingsRepository;
import com.sacolao.payment.repository.PaymentRepository;
import com.sacolao.payment.repository.PaymentWebhookEventRepository;
import com.sacolao.tenant.TenantContext;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final EstablishmentPaymentSettingsRepository settingsRepository;
    private final PaymentWebhookEventRepository webhookEventRepository;
    private final EstablishmentRepository establishmentRepository;
    private final OrderRepository orderRepository;
    private final MercadoPagoPaymentGateway mercadoPagoPaymentGateway;
    private final MockPaymentGateway mockPaymentGateway;
    private final ManualPaymentGateway manualPaymentGateway;
    private final JsonMapper jsonMapper;

    public PaymentService(
            PaymentRepository paymentRepository,
            EstablishmentPaymentSettingsRepository settingsRepository,
            PaymentWebhookEventRepository webhookEventRepository,
            EstablishmentRepository establishmentRepository,
            OrderRepository orderRepository,
            MercadoPagoPaymentGateway mercadoPagoPaymentGateway,
            MockPaymentGateway mockPaymentGateway,
            ManualPaymentGateway manualPaymentGateway,
            JsonMapper jsonMapper
    ) {
        this.paymentRepository = paymentRepository;
        this.settingsRepository = settingsRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.establishmentRepository = establishmentRepository;
        this.orderRepository = orderRepository;
        this.mercadoPagoPaymentGateway = mercadoPagoPaymentGateway;
        this.mockPaymentGateway = mockPaymentGateway;
        this.manualPaymentGateway = manualPaymentGateway;
        this.jsonMapper = jsonMapper;
    }

    @Transactional
    public Payment createForOrder(Order order, String idempotencyKey) {
        EstablishmentPaymentSettings settings = requireSettings(order.getEstablishment());
        PaymentGateway gateway = resolveGateway(settings, order.getPaymentMethod());
        PaymentGateway.ChargeResult result = gateway.charge(new PaymentGateway.ChargeRequest(
                order,
                settings,
                order.getPaymentMethod(),
                order.getTotal(),
                idempotencyKey == null ? order.getId().toString() : idempotencyKey
        ));

        Payment payment = new Payment();
        payment.setEstablishment(order.getEstablishment());
        payment.setOrder(order);
        payment.setProvider(gateway.provider());
        payment.setStatus(result.status());
        payment.setMethod(order.getPaymentMethod());
        payment.setAmount(order.getTotal());
        payment.setCurrency("BRL");
        payment.setExternalId(result.externalId());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setPixCopyPaste(result.pixCopyPaste());
        payment.setPixQrCodeBase64(result.pixQrCodeBase64());
        payment.setCheckoutUrl(result.checkoutUrl());
        payment.setRawResponse(result.rawResponse());
        if (result.status() == PaymentStatus.PAID) {
            payment.setPaidAt(Instant.now());
            markOrderConfirmed(order);
        }
        return paymentRepository.save(payment);
    }

    @Transactional(readOnly = true)
    public PaymentResponse getByOrder(UUID orderId) {
        return paymentRepository.findByOrder_IdAndEstablishment_Id(orderId, TenantContext.require())
                .map(PaymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }

    @Transactional(readOnly = true)
    public PaymentResponse getPublic(String slug, String publicCode) {
        Establishment store = requireActiveStore(slug);
        return paymentRepository.findByOrderPublicCodeAndEstablishmentId(
                        publicCode.trim().toUpperCase(Locale.ROOT),
                        store.getId()
                )
                .map(PaymentMapper::toResponse)
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }

    @Transactional
    public PaymentResponse confirmManual(UUID orderId) {
        Payment payment = paymentRepository.findByOrder_IdAndEstablishment_Id(orderId, TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentMapper.toResponse(payment);
        }
        if (payment.getMethod() != PaymentMethod.CASH
                && payment.getMethod() != PaymentMethod.ON_DELIVERY
                && payment.getProvider() != PaymentProviderType.MOCK
                && payment.getProvider() != PaymentProviderType.MANUAL) {
            throw new UnprocessableException("MANUAL_CONFIRM_NOT_ALLOWED", "Este pagamento deve ser confirmado pelo gateway");
        }
        return markPaid(payment, "manual-confirm-" + Instant.now().toEpochMilli(), "{\"source\":\"manual\"}");
    }

    @Transactional
    public PaymentResponse simulatePaid(String slug, String publicCode) {
        Establishment store = requireActiveStore(slug);
        Payment payment = paymentRepository.findByOrderPublicCodeAndEstablishmentId(
                        publicCode.trim().toUpperCase(Locale.ROOT),
                        store.getId()
                )
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
        if (payment.getProvider() != PaymentProviderType.MOCK && !requireSettings(store).isMockMode()) {
            throw new UnprocessableException("SIMULATE_DISABLED", "Simulação disponível apenas em modo mock");
        }
        return markPaid(payment, "simulate-" + Instant.now().toEpochMilli(), "{\"source\":\"simulate\"}");
    }

    @Transactional
    public void handleWebhook(PaymentProviderType provider, String providerEventId, String payload) {
        if (providerEventId == null || providerEventId.isBlank()) {
            providerEventId = IdempotencyService.sha256(payload);
        }
        var existing = webhookEventRepository.findByProviderAndProviderEventId(provider, providerEventId);
        if (existing.isPresent() && existing.get().isProcessed()) {
            return;
        }
        PaymentWebhookEvent event = existing.orElseGet(PaymentWebhookEvent::new);
        event.setProvider(provider);
        event.setProviderEventId(providerEventId);
        event.setPayload(payload);

        try {
            JsonNode json = jsonMapper.readTree(payload == null ? "{}" : payload);
            String externalId = firstNonBlank(
                    text(json, "data.id"),
                    text(json, "data.payment.id"),
                    text(json, "id"),
                    text(json, "externalId")
            );
            String status = firstNonBlank(
                    text(json, "action"),
                    text(json, "data.status"),
                    text(json, "status")
            );
            Payment payment = null;
            if (externalId != null) {
                payment = paymentRepository.findByProviderAndExternalId(provider, externalId)
                        .or(() -> paymentRepository.findByExternalId(externalId))
                        .orElse(null);
            }
            if (payment != null) {
                event.setPayment(payment);
                event.setEstablishment(payment.getEstablishment());
                if (isPaidSignal(status) || "payment.updated".equalsIgnoreCase(status)) {
                    markPaid(payment, externalId, payload);
                }
            }
            event.setProcessed(true);
            event.setProcessedAt(Instant.now());
            webhookEventRepository.save(event);
        } catch (Exception ex) {
            event.setProcessed(false);
            webhookEventRepository.save(event);
            throw new UnprocessableException("WEBHOOK_INVALID", "Webhook inválido");
        }
    }

    @Transactional(readOnly = true)
    public PaymentSettingsResponse getSettings() {
        return PaymentMapper.toSettingsResponse(requireSettings(requireTenantEstablishment()));
    }

    @Transactional
    public PaymentSettingsResponse updateSettings(UpdatePaymentSettingsRequest request) {
        Establishment establishment = requireTenantEstablishment();
        EstablishmentPaymentSettings settings = settingsRepository.findById(establishment.getId())
                .orElseGet(() -> {
                    EstablishmentPaymentSettings created = new EstablishmentPaymentSettings();
                    created.setEstablishmentId(establishment.getId());
                    return created;
                });
        settings.setProvider(request.provider());
        if (request.accessToken() != null) {
            settings.setAccessToken(blankToNull(request.accessToken()));
        }
        if (request.webhookSecret() != null) {
            settings.setWebhookSecret(blankToNull(request.webhookSecret()));
        }
        if (request.pixEnabled() != null) {
            settings.setPixEnabled(request.pixEnabled());
        }
        if (request.onlineEnabled() != null) {
            settings.setOnlineEnabled(request.onlineEnabled());
        }
        if (request.mockMode() != null) {
            settings.setMockMode(request.mockMode());
        }
        return PaymentMapper.toSettingsResponse(settingsRepository.save(settings));
    }

    private PaymentResponse markPaid(Payment payment, String externalId, String raw) {
        if (payment.getStatus() == PaymentStatus.PAID) {
            return PaymentMapper.toResponse(payment);
        }
        payment.setStatus(PaymentStatus.PAID);
        payment.setPaidAt(Instant.now());
        if (payment.getExternalId() == null && externalId != null) {
            payment.setExternalId(externalId);
        }
        if (raw != null) {
            payment.setRawResponse(raw);
        }
        markOrderConfirmed(payment.getOrder());
        return PaymentMapper.toResponse(paymentRepository.save(payment));
    }

    private void markOrderConfirmed(Order order) {
        if (order.getStatus() == OrderStatus.PENDING) {
            order.setStatus(OrderStatus.CONFIRMED);
            orderRepository.save(order);
        }
    }

    private PaymentGateway resolveGateway(EstablishmentPaymentSettings settings, PaymentMethod method) {
        if (method == PaymentMethod.CASH || method == PaymentMethod.ON_DELIVERY) {
            return manualPaymentGateway;
        }
        if (settings.isMockMode()
                || settings.getAccessToken() == null
                || settings.getAccessToken().isBlank()
                || settings.getProvider() == PaymentProviderType.MOCK) {
            return mockPaymentGateway;
        }
        if (settings.getProvider() == PaymentProviderType.MERCADO_PAGO) {
            return mercadoPagoPaymentGateway;
        }
        return manualPaymentGateway;
    }

    private EstablishmentPaymentSettings requireSettings(Establishment establishment) {
        return settingsRepository.findById(establishment.getId()).orElseGet(() -> {
            EstablishmentPaymentSettings created = new EstablishmentPaymentSettings();
            created.setEstablishmentId(establishment.getId());
            created.setProvider(PaymentProviderType.MERCADO_PAGO);
            created.setMockMode(true);
            created.setPixEnabled(true);
            return settingsRepository.save(created);
        });
    }

    private Establishment requireTenantEstablishment() {
        return establishmentRepository.findById(TenantContext.require())
                .orElseThrow(() -> new ResourceNotFoundException("Recurso não encontrado"));
    }

    private Establishment requireActiveStore(String slug) {
        return establishmentRepository.findBySlug(slug)
                .filter(Establishment::isActive)
                .orElseThrow(() -> new ResourceNotFoundException("Loja não encontrada"));
    }

    private static boolean isPaidSignal(String status) {
        if (status == null) {
            return false;
        }
        String normalized = status.toLowerCase(Locale.ROOT);
        return normalized.contains("approved")
                || normalized.equals("paid")
                || normalized.equals("payment.created");
    }

    private static String text(JsonNode node, String path) {
        if (node == null) {
            return null;
        }
        JsonNode current = node;
        for (String part : path.split("\\.")) {
            current = current.path(part);
        }
        if (current.isMissingNode() || current.isNull()) {
            return null;
        }
        return current.asString(null);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
