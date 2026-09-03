package com.sacolao.catalog;

import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIT extends CatalogSupport {

    @Test
    void ownerCanCreateCategoryAndProduct() throws Exception {
        var registered = AuthApi.register(mockMvc, "Horta Catalogo", "Ana", AuthApi.uniqueEmail("cat"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String categoryId = createCategory(token, "Frutas");
        String productId = createProduct(token, categoryId, "Banana prata", "6.90", "KG");

        mockMvc.perform(get("/api/v1/products/" + productId)
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Banana prata"))
                .andExpect(jsonPath("$.unit").value("KG"))
                .andExpect(jsonPath("$.price").value(6.90));
    }

    @Test
    void duplicateCategoryNameIsRejected() throws Exception {
        var registered = AuthApi.register(mockMvc, "Horta Dup Cat", "Bia", AuthApi.uniqueEmail("dupcat"), "senha12345");
        String token = AuthApi.accessToken(registered);
        createCategory(token, "Legumes");
        mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"legumes"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    void publicStoreHidesUnavailableProducts() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Vitrine", "Caio", AuthApi.uniqueEmail("vitrine"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String storeSlug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Folhas");
        String visibleId = createProduct(token, categoryId, "Alface", "3.50", "UN");
        String hiddenId = createProduct(token, categoryId, "Rúcula", "4.00", "UN");
        mockMvc.perform(patch("/api/v1/products/" + hiddenId + "/availability")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"value":false}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/store/" + storeSlug + "/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].id").value(visibleId));

        mockMvc.perform(get("/api/v1/store/" + storeSlug + "/products/" + hiddenId))
                .andExpect(status().isNotFound());
    }

    @Test
    void cartQuoteUsesServerPrice() throws Exception {
        var registered = AuthApi.register(mockMvc, "Loja Quote", "Dora", AuthApi.uniqueEmail("quote"), "senha12345");
        String token = AuthApi.accessToken(registered);
        String storeSlug = AuthApi.establishmentSlug(registered);
        String categoryId = createCategory(token, "Raízes");
        String productId = createProduct(token, categoryId, "Batata", "4.00", "KG");

        mockMvc.perform(post("/api/v1/store/" + storeSlug + "/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":2.5}]}
                                """.formatted(productId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].unitPrice").value(4.00))
                .andExpect(jsonPath("$.subtotal").value(10.00))
                .andExpect(jsonPath("$.total").value(10.00));
    }

    @Test
    void unknownStoreReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/store/loja-que-nao-existe/catalog"))
                .andExpect(status().isNotFound());
    }
}
