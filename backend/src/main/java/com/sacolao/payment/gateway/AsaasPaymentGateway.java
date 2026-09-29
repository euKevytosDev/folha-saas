package com.sacolao.payment.gateway;

import com.sacolao.common.exception.UnprocessableException;
import com.sacolao.order.entity.PaymentMethod;
import com.sacolao.payment.entity.EstablishmentPaymentSettings;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

@Component
public class AsaasPaymentGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(AsaasPaymentGateway.class);
    private static final ZoneId STORE_ZONE = ZoneId.of("America/Sao_Paulo");

    private final RestClient.Builder restClientBuilder;
    private final JsonMapper jsonMapper;
    private final MockPaymentGateway mockPaymentGateway;

    public AsaasPaymentGateway(
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
        return PaymentProviderType.ASAAS;
    }

    @Override
    public ChargeResult charge(ChargeRequest request) {
        String token = request.settings().getAccessToken();
        if (token == null || token.isBlank() || request.settings().isMockMode()) {
            return mockPaymentGateway.charge(request);
        }
        if (request.method() != PaymentMethod.PIX) {
            return ChargeResult.pendingManual("asaas-manual-" + UUID.randomUUID());
        }
        String cpf = digits(request.order().getCustomerCpf());
        if (cpf.length() != 11) {
            throw new UnprocessableException("CPF_REQUIRED", "Informe o CPF do cliente para gerar o PIX");
        }
        try {
            String base = request.settings().isSandbox()
                    ? "https://api-sandbox.asaas.com/v3"
                    : "https://api.asaas.com/v3";
            RestClient client = restClientBuilder.build();
            String customerId = findOrCreateCustomer(client, base, token.trim(), request, cpf);
            ObjectNode body = jsonMapper.createObjectNode();
            body.put("customer", customerId);
            body.put("billingType", "PIX");
            body.put("value", request.amount());
            body.put("dueDate", LocalDate.now(STORE_ZONE).toString());
            body.put("description", "Pedido " + request.order().getPublicCode());
            body.put("externalReference", request.order().getId().toString());

            String paymentRaw = post(client, base + "/payments", token.trim(), body);
            JsonNode payment = jsonMapper.readTree(paymentRaw == null ? "{}" : paymentRaw);
            String paymentId = text(payment, "id");
            if (paymentId == null || paymentId.isBlank()) {
                throw new UnprocessableException("GATEWAY_ERROR", "A Asaas não devolveu a cobrança");
            }
            String qrRaw = client.get()
                    .uri(base + "/payments/" + paymentId + "/pixQrCode")
                    .header("access_token", token.trim())
                    .header("User-Agent", "Folha")
                    .retrieve()
                    .body(String.class);
            JsonNode qr = jsonMapper.readTree(qrRaw == null ? "{}" : qrRaw);
            return new ChargeResult(
                    PaymentStatus.PENDING,
                    paymentId,
                    text(qr, "payload"),
                    text(qr, "encodedImage"),
                    null,
                    paymentRaw
            );
        } catch (UnprocessableException ex) {
            throw ex;
        } catch (RestClientResponseException ex) {
            log.warn("Asaas rejeitou cobrança: {}", ex.getResponseBodyAsString());
            throw new UnprocessableException("GATEWAY_ERROR", "Falha ao criar cobrança na Asaas");
        } catch (Exception ex) {
            log.warn("Erro ao chamar Asaas", ex);
            throw new UnprocessableException("GATEWAY_ERROR", "Falha ao criar cobrança na Asaas");
        }
    }

    private String findOrCreateCustomer(RestClient client, String base, String token, ChargeRequest request, String cpf) {
        String listed = client.get()
                .uri(base + "/customers?cpfCnpj=" + cpf)
                .header("access_token", token)
                .header("User-Agent", "Folha")
                .retrieve()
                .body(String.class);
        JsonNode list = jsonMapper.readTree(listed == null ? "{}" : listed);
        JsonNode data = list.path("data");
        if (data.isArray() && !data.isEmpty()) {
            String existing = text(data.get(0), "id");
            if (existing != null && !existing.isBlank()) {
                return existing;
            }
        }
        ObjectNode customer = jsonMapper.createObjectNode();
        customer.put("name", request.order().getCustomerName());
        customer.put("cpfCnpj", cpf);
        customer.put("notificationDisabled", true);
        String email = request.order().getCustomerEmail();
        if (email != null && !email.isBlank()) {
            customer.put("email", email.trim());
        }
        String phone = digits(request.order().getCustomerPhone());
        if (phone.startsWith("55") && phone.length() > 11) {
            phone = phone.substring(2);
        }
        if (phone.length() >= 10) {
            customer.put("mobilePhone", phone);
        }
        JsonNode created = jsonMapper.readTree(post(client, base + "/customers", token, customer));
        String id = text(created, "id");
        if (id == null || id.isBlank()) {
            throw new UnprocessableException("GATEWAY_ERROR", "A Asaas não devolveu o cliente");
        }
        return id;
    }

    private String post(RestClient client, String url, String token, ObjectNode body) {
        return client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .header("access_token", token)
                .header("User-Agent", "Folha")
                .body(body)
                .retrieve()
                .body(String.class);
    }

    private static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    private static String text(JsonNode node, String field) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        JsonNode child = node.get(field);
        return child == null || child.isNull() ? null : child.asString();
    }
}
