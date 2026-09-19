package com.sacolao.order;

import com.sacolao.catalog.CatalogSupport;
import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class OrderIT extends CatalogSupport {

    @Test
    void checkoutRecalculatesPricesAndCreatesCustomer() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Pedido", "Eva", AuthApi.uniqueEmail("order"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Frutas");
        String productId = createProduct(token, categoryId, "Mamão", "5.00", "KG");

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1.5}],
                                  "customerName":"Cliente Teste",
                                  "customerPhone":"(11) 98888-7777",
                                  "customerEmail":"cliente@exemplo.com",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"PIX",
                                  "notes":"Sem saco plástico"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.publicCode").isNotEmpty())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.subtotal").value(7.50))
                .andExpect(jsonPath("$.total").value(7.50))
                .andExpect(jsonPath("$.items", hasSize(1)))
                .andExpect(jsonPath("$.items[0].unitPrice").value(5.00))
                .andExpect(jsonPath("$.items[0].productName").value("Mamão"));

        mockMvc.perform(get("/api/v1/customers")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].phone").value("11988887777"));

        mockMvc.perform(get("/api/v1/orders/summary")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.pending").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void deliveryRequiresAddress() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Entrega", "Fred", AuthApi.uniqueEmail("deliv"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Verduras");
        String productId = createProduct(token, categoryId, "Alface", "3.00", "UN");

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11999990000",
                                  "fulfillmentType":"DELIVERY",
                                  "paymentMethod":"ON_DELIVERY"
                                }
                                """.formatted(productId)))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void ownerCanAdvanceStatus() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Status", "Gabi", AuthApi.uniqueEmail("status"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Raízes");
        String productId = createProduct(token, categoryId, "Cenoura", "4.00", "KG");

        String orderId = AuthApi.read(mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11911112222",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"CASH"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"CONFIRMED"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        mockMvc.perform(patch("/api/v1/orders/" + orderId + "/status")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"status":"DELIVERED"}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }
}
