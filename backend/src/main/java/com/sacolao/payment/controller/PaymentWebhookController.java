package com.sacolao.payment.controller;

import com.sacolao.payment.entity.PaymentProviderType;
import com.sacolao.payment.service.PaymentService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/webhooks")
public class PaymentWebhookController {

    private final PaymentService paymentService;
    private final JsonMapper jsonMapper;

    public PaymentWebhookController(PaymentService paymentService, JsonMapper jsonMapper) {
        this.paymentService = paymentService;
        this.jsonMapper = jsonMapper;
    }

    @PostMapping("/mercadopago")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> mercadoPago(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey
    ) {
        String eventId = firstNonBlank(requestId, idempotencyKey);
        paymentService.handleWebhook(PaymentProviderType.MERCADO_PAGO, eventId, toPayload(body));
        return Map.of("status", "ok");
    }

    @PostMapping("/mock")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> mock(
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-Event-Id", required = false) String eventId
    ) {
        paymentService.handleWebhook(PaymentProviderType.MOCK, eventId, toPayload(body));
        return Map.of("status", "ok");
    }

    @PostMapping("/{provider}")
    @ResponseStatus(HttpStatus.OK)
    public Map<String, String> generic(
            @PathVariable String provider,
            @RequestBody(required = false) JsonNode body,
            @RequestHeader(value = "X-Event-Id", required = false) String eventId
    ) {
        PaymentProviderType type = switch (provider.toLowerCase()) {
            case "mercado-pago", "mercadopago", "mp" -> PaymentProviderType.MERCADO_PAGO;
            case "manual" -> PaymentProviderType.MANUAL;
            default -> PaymentProviderType.MOCK;
        };
        paymentService.handleWebhook(type, eventId, toPayload(body));
        return Map.of("status", "ok");
    }

    private String toPayload(JsonNode body) {
        if (body == null || body.isNull() || body.isMissingNode()) {
            return "{}";
        }
        return jsonMapper.writeValueAsString(body);
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return null;
    }
}
