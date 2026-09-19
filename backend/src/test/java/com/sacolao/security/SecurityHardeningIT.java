package com.sacolao.security;

import com.sacolao.catalog.CatalogSupport;
import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payments.simulate-enabled=false",
        "app.payments.mock-webhooks-enabled=false",
        "app.payments.require-webhook-secret=true"
})
class SecurityHardeningIT extends CatalogSupport {

    @Test
    void simulateIsDisabledWhenConfigured() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Sec", "Sec", AuthApi.uniqueEmail("sec"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Frutas");
        String productId = createProduct(token, categoryId, "Maçã", "4.00", "KG");

        String publicCode = AuthApi.read(mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11970009999",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"PIX"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.publicCode");

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders/" + publicCode + "/payment/simulate"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("SIMULATE_DISABLED"));
    }

    @Test
    void mockWebhookIsDisabledWhenConfigured() throws Exception {
        mockMvc.perform(post("/api/v1/webhooks/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Webhook-Secret", "anything")
                        .content("""
                                {"id":"x","status":"approved"}
                                """))
                .andExpect(status().isUnauthorized());
    }
}
