package com.sacolao.phase6;

import com.sacolao.catalog.CatalogSupport;
import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class Phase6IT extends CatalogSupport {

    @Test
    void fixedDeliveryFeeAndCouponApplyOnCheckout() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Frete", "Frete", AuthApi.uniqueEmail("frete"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Frutas");
        String productId = createProduct(token, categoryId, "Laranja", "10.00", "KG");

        mockMvc.perform(put("/api/v1/delivery/settings")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryEnabled":true,
                                  "pickupEnabled":true,
                                  "fixedFee":7.50,
                                  "freeAboveAmount":null,
                                  "estimatedMinutes":40
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fixedFee").value(7.50));

        mockMvc.perform(post("/api/v1/coupons")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code":"DESCONTO5",
                                  "discountType":"FIXED",
                                  "discountValue":5.00,
                                  "active":true
                                }
                                """))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/store/" + slug + "/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "fulfillmentType":"DELIVERY",
                                  "couponCode":"DESCONTO5"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(10.00))
                .andExpect(jsonPath("$.discount").value(5.00))
                .andExpect(jsonPath("$.deliveryFee").value(7.50))
                .andExpect(jsonPath("$.total").value(12.50))
                .andExpect(jsonPath("$.couponCode").value("DESCONTO5"));

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente Frete",
                                  "customerPhone":"11970001111",
                                  "fulfillmentType":"DELIVERY",
                                  "paymentMethod":"PIX",
                                  "couponCode":"DESCONTO5",
                                  "addressStreet":"Rua A",
                                  "addressNumber":"10",
                                  "addressNeighborhood":"Centro",
                                  "addressCity":"São Paulo",
                                  "addressState":"SP"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.deliveryFee").value(7.50))
                .andExpect(jsonPath("$.discount").value(5.00))
                .andExpect(jsonPath("$.total").value(12.50))
                .andExpect(jsonPath("$.couponCode").value("DESCONTO5"));
    }

    @Test
    void freeDeliveryAboveThreshold() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja FreeShip", "Free", AuthApi.uniqueEmail("freeship"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Verduras");
        String productId = createProduct(token, categoryId, "Couve", "20.00", "UN");

        mockMvc.perform(put("/api/v1/delivery/settings")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryEnabled":true,
                                  "pickupEnabled":true,
                                  "fixedFee":9.00,
                                  "freeAboveAmount":30.00,
                                  "estimatedMinutes":null
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/store/" + slug + "/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":2}],
                                  "fulfillmentType":"DELIVERY"
                                }
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.subtotal").value(40.00))
                .andExpect(jsonPath("$.deliveryFee").value(0.00))
                .andExpect(jsonPath("$.total").value(40.00));
    }

    @Test
    void stockControlledBlocksOversellAndMarksUnavailable() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Estoque", "Est", AuthApi.uniqueEmail("stock"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Frutas");

        String productId = AuthApi.read(mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"%s",
                                  "name":"Mamão",
                                  "price":5.00,
                                  "unit":"UN",
                                  "available":true,
                                  "featured":false,
                                  "stockControlled":true,
                                  "stockQuantity":1,
                                  "minimumQuantity":1
                                }
                                """.formatted(categoryId)))
                .andExpect(status().isCreated())
                .andReturn(), "$.id");

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente",
                                  "customerPhone":"11980001111",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"CASH"
                                }
                                """.formatted(productId)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(0))
                .andExpect(jsonPath("$.available").value(false));

        mockMvc.perform(post("/api/v1/store/" + slug + "/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "items":[{"productId":"%s","quantity":1}],
                                  "customerName":"Cliente 2",
                                  "customerPhone":"11980002222",
                                  "fulfillmentType":"PICKUP",
                                  "paymentMethod":"CASH"
                                }
                                """.formatted(productId)))
                .andExpect(status().isUnprocessableEntity());

        mockMvc.perform(patch("/api/v1/products/" + productId + "/stock")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"quantity":3,"note":"reposição"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQuantity").value(3));
    }

    @Test
    void publicCatalogIncludesDeliverySettings() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja PubDel", "Pub", AuthApi.uniqueEmail("pubdel"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String slug = AuthApi.establishmentSlug(registered);

        mockMvc.perform(put("/api/v1/delivery/settings")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "deliveryEnabled":true,
                                  "pickupEnabled":false,
                                  "fixedFee":12.00,
                                  "freeAboveAmount":100.00,
                                  "estimatedMinutes":50
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/store/" + slug + "/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.store.delivery.fixedFee").value(12.00))
                .andExpect(jsonPath("$.store.delivery.pickupEnabled").value(false));
    }
}
