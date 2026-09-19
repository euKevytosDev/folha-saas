package com.sacolao.payment;

import com.sacolao.catalog.CatalogSupport;
import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.payments.require-webhook-secret=true",
        "app.payments.mock-webhooks-enabled=true"
})
class WebhookAuthIT extends CatalogSupport {

    @Test
    void rejectsMockWebhookWithoutSecret() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja WHSec", "WhSec", AuthApi.uniqueEmail("whsec"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Frutas");
        String productId = createProduct(token, categoryId, "Limão", "2.00", "KG");

        String publicCode = AuthApi.read(mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11966667777",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"PIX"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.publicCode");

        String externalId = AuthApi.read(mockMvc.perform(get("/api/v1/store/" + slug + "/orders/" + publicCode + "/payment"))
                        .andExpect(status().isOk())
                        .andReturn(), "$.externalId");

        mockMvc.perform(post("/api/v1/webhooks/mock")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"id":"%s","status":"approved"}
                                """.formatted(externalId)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }
}
