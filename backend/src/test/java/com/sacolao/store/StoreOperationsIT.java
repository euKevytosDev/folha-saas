package com.sacolao.store;

import com.sacolao.catalog.CatalogSupport;
import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoreOperationsIT extends CatalogSupport {

    @Test
    void closedStoreRejectsCheckout() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Fechada", "Dono", AuthApi.uniqueEmail("closed"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String establishmentId = AuthApi.establishmentId(registered);
        String categoryId = createCategory(token, "Frutas");
        String productId = createProduct(token, categoryId, "Banana", "5.00", "KG");

        mockMvc.perform(put("/api/v1/establishments/" + establishmentId)
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeOpenMode":"CLOSED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.acceptingOrders").value(false));

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11988887777",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"PIX"
                                }
                                """.formatted(productId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("STORE_CLOSED"));
    }

    @Test
    void minOrderRejectsCheckout() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Min", "Dono", AuthApi.uniqueEmail("minord"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Verduras");
        String productId = createProduct(token, categoryId, "Alface", "4.00", "UN");

        mockMvc.perform(put("/api/v1/delivery/settings")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryEnabled":true,
                                  "pickupEnabled":true,
                                  "fixedFee":0,
                                  "minOrderAmount":50.00,
                                  "pickupEtaMinutes":30,
                                  "deliveryEtaMinutes":60
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minOrderAmount").value(50.00));

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11977776666",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"CASH"
                                }
                                """.formatted(productId)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("MIN_ORDER"));
    }
}
