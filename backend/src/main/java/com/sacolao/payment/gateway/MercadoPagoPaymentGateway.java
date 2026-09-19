package com.sacolao.payment.gateway;

import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.payment.entity.PaymentProviderType;
import com.sacolao.payment.entity.PaymentStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

import java.util.UUID;

@Component
public class MercadoPagoPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoPaymentGateway.class);

    private final RestClient.Builder restClientBuilder;
    private final JsonMapper jsonMapper;
    private final MockPaymentGateway mockPaymentGateway;

    public MercadoPagoPaymentGateway(
            RestClient.Builder restClientBuilder,
            JsonMapper jsonMapper,
            MockPaymentGateway mockPaymentGateway
    ) {
        this.restClientBuilder = restClientBuilder;
        this.jsonMapper = jsonMapper;
        this.mockPaymentGateway = mockPaymentGateway;
    }

    @Override
    public PaymentProviderType provider() {
        return PaymentProviderType.MERCADO_PAGO;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String token = request.settings().getAccessToken();
        if (token == null || token.isBlank() || request.settings().isMockMode()) {
            return mockPaymentGateway.charge(request);
        }
        if (request.method() != PaymentMethod.PIX) {
            return ChargeResult.pendingManual("mp-manual-" + UUID.randomUUID());
        }
        try {
            ObjectNode body = jsonMapper.createObjectNode();
            body.put("transaction_amount", request.amount());
            body.put("description", "Pedido " + request.order().getPublicCode());
            body.put("payment_method_id", "pix");
            ObjectNode payer = body.putObject("payer");
            String email = request.order().getCustomerEmail();
            payer.put("email", email == null || email.isBlank()
                    ? "cliente+" + request.order().getPublicCode().toLowerCase() + "@folha.app"
                    : email);
            ObjectNode identification = payer.putObject("identification");
            identification.put("type", "CPF");
            identification.put("number", "19119119100");

            String raw = restClientBuilder.build()
                    .post()
                    .uri("https://api.mercadopago.com/v1/payments")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token.trim())
                    .header("X-Idempotency-Key", request.idempotencyKey() == null
                            ? UUID.randomUUID().toString()
                            : request.idempotencyKey())
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode json = jsonMapper.readTree(raw == null ? "{}" : raw);
            String externalId = text(json, "id");
            String status = text(json.path("status"), null);
            JsonNode txData = json.path("point_of_interaction").path("transaction_data");
            String copyPaste = text(txData, "qr_code");
            String qrBase64 = text(txData, "qr_code_base64");
            PaymentStatus mapped = mapStatus(status);
            return new ChargeResult(mapped, externalId, copyPaste, qrBase64, null, raw);
        } catch (RestClientResponseException ex) {
            log.warn("Mercado Pago rejeitou cobrança: {}", ex.getResponseBodyAsString());
            throw new UnprocessableException("GATEWAY_ERROR", "Falha ao criar cobrança no Mercado Pago");
        } catch (Exception ex) {
            log.warn("Erro ao chamar Mercado Pago", ex);
            throw new UnprocessableException("GATEWAY_ERROR", "Falha ao criar cobrança no Mercado Pago");
        }
    }

    private static PaymentStatus mapStatus(String status) {
        if (status == null) {
            return PaymentStatus.PENDING;
        }
        return switch (status.toLowerCase()) {
            case "approved" -> PaymentStatus.PAID;
            case "authorized" -> PaymentStatus.AUTHORIZED;
            case "rejected", "cancelled" -> PaymentStatus.FAILED;
            case "refunded" -> PaymentStatus.REFUNDED;
            default -> PaymentStatus.PENDING;
        };
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (field == null) {
            return node.isValueNode() ? node.asString() : null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asString();
    }
}
