package com.sacolao.order;

import com.sacolao.catalog.CatalogSupport;
import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIsolationIT extends CatalogSupport {

    @Test
    void tenantCannotReadOtherTenantOrders() throws Exception {
        var tenantA = AuthApi.register(mockMvc, "Loja A Pedidos", "Ana", AuthApi.uniqueEmail("orda"), "senha12345");
        var tenantB = AuthApi.register(mockMvc, "Loja B Pedidos", "Bia", AuthApi.uniqueEmail("ordb"), "senha12345");
        String tokenA = AuthApi.accessToken(tenantA);
        String tokenB = AuthApi.accessToken(tenantB);
        String slugA = AuthApi.establishmentSlug(tenantA);

        String categoryId = createCategory(tokenA, "Frutas");
        String productId = createProduct(tokenA, categoryId, "Banana", "6.00", "KG");

        MvcResult created = mockMvc.perform(post("/api/v1/store/" + slugA + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente A",
                                  "customerPhone":"11900001111",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"PIX"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn();
        String orderId = AuthApi.read(created, "$.id");
        String publicCode = AuthApi.read(created, "$.publicCode");

        mockMvc.perform(get("/api/v1/orders/" + orderId)
                        .header("Authorization", AuthApi.bearer(tokenB)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/store/" + AuthApi.establishmentSlug(tenantB) + "/orders/" + publicCode))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/orders")
                        .header("Authorization", AuthApi.bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(result -> {
                    String body = result.getResponse().getContentAsString();
                    if (body.contains(orderId)) {
                        throw new AssertionError("Pedido do tenant A vazou para o tenant B");
                    }
                });
    }
}
