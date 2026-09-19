package com.sacolao.payment;

import com.sacolao.catalog.CatalogSupport;
import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PaymentIT extends CatalogSupport {

    @Test
    void checkoutCreatesMockPixPayment() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja PIX", "Pix", AuthApi.uniqueEmail("pix"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Frutas");
        String productId = createProduct(token, categoryId, "Uva", "10.00", "KG");

        String publicCode = AuthApi.read(mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "pix-key-1")
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente PIX",
                                  "customerPhone":"11922223333",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"PIX"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.payment").value(notNullValue()))
                .andExpect(jsonPath("$.payment.status").value("PENDING"))
                .andExpect(jsonPath("$.payment.provider").value("MOCK"))
                .andExpect(jsonPath("$.payment.pixCopyPaste").isNotEmpty())
                .andReturn(), "$.publicCode");

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders/" + publicCode + "/payment/simulate"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/v1/store/" + slug + "/orders/" + publicCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("PAID"));
    }

    @Test
    void idempotentCheckoutReturnsSameOrder() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Idem", "Idem", AuthApi.uniqueEmail("idem"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Verduras");
        String productId = createProduct(token, categoryId, "Couve", "2.00", "UN");
        String payload = """
                {
                  "items":[{"productId":"%s","quantity":1}],
                  "customerName":"Cliente",
                  "customerPhone":"11933334444",
                  "fulfillmentType":"PICKUP",
                  "paymentMethod":"CASH"
                }
                """.formatted(productId);

        String firstId = AuthApi.read(mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key")
                        .content(payload))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Idempotency-Key", "same-key")
                        .content(payload))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(firstId));
    }

    @Test
    void webhookConfirmsPayment() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja WH", "Wh", AuthApi.uniqueEmail("wh"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Raízes");
        String productId = createProduct(token, categoryId, "Batata", "3.00", "KG");

        String publicCode = AuthApi.read(mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11944445555",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"PIX"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.publicCode");

        String externalId = AuthApi.read(mockMvc.perform(get("/api/v1/store/" + slug + "/orders/" + publicCode + "/payment"))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value("PENDING"))
                        .andReturn(), "$.externalId");

        String eventId = "evt-" + java.util.UUID.randomUUID();
        mockMvc.perform(post("/api/v1/webhooks/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Event-Id", eventId)
                        .content("""
                                {"id":"%s","status":"approved"}
                                """.formatted(externalId)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/webhooks/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Event-Id", eventId)
                        .content("""
                                {"id":"%s","status":"approved"}
                                """.formatted(externalId)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/store/" + slug + "/orders/" + publicCode + "/payment"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));

        mockMvc.perform(get("/api/v1/store/" + slug + "/orders/" + publicCode))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.payment.status").value("PAID"));
    }

    @Test
    void ownerCanUpdatePaymentSettings() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja PayCfg", "Cfg", AuthApi.uniqueEmail("paycfg"), "senha12345");
        String token = AuthApi.accessToken(registered);

        mockMvc.perform(get("/api/v1/payments/settings")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mockMode").value(true));

        mockMvc.perform(put("/api/v1/payments/settings")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "provider":"MERCADO_PAGO",
                                  "accessToken":"TEST-TOKEN",
                                  "pixEnabled":true,
                                  "onlineEnabled":false,
                                  "mockMode":true
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessTokenConfigured").value(true))
                .andExpect(jsonPath("$.provider").value("MERCADO_PAGO"));
    }

    @Test
    void tenantCannotConfirmOtherTenantPayment() throws Exception {
        var tenantA = AuthApi.register(mockMvc, "Loja Pay A", "PayA", AuthApi.uniqueEmail("paya"), "senha12345");
        var tenantB = AuthApi.register(mockMvc, "Loja Pay B", "PayB", AuthApi.uniqueEmail("payb"), "senha12345");
        String tokenA = AuthApi.accessToken(tenantA);
        String tokenB = AuthApi.accessToken(tenantB);
        String slugA = AuthApi.establishmentSlug(tenantA);
        String categoryId = createCategory(tokenA, "Frutas");
        String productId = createProduct(tokenA, categoryId, "Maçã", "4.00", "KG");

        String orderId = AuthApi.read(mockMvc.perform(post("/api/v1/store/" + slugA + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11955556666",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"CASH"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(post("/api/v1/orders/" + orderId + "/payment/confirm")
                        .header("Authorization", AuthApi.bearer(tokenB)))
                .andExpect(status().isNotFound());
    }
}
