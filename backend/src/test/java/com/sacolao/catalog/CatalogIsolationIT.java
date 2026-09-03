package com.sacolao.catalog;

import com.sacolao.support.AuthApi;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class CatalogIsolationIT extends CatalogSupport {

    @Test
    void ownerOfTenantACannotReadOrChangeCatalogOfTenantB() throws Exception {
        MvcResult tenantA = AuthApi.register(mockMvc, "Catalogo Alfa", "Owner A", AuthApi.uniqueEmail("cata"), "senha12345");
        MvcResult tenantB = AuthApi.register(mockMvc, "Catalogo Beta", "Owner B", AuthApi.uniqueEmail("catb"), "senha12345");
        String tokenA = AuthApi.accessToken(tenantA);
        String tokenB = AuthApi.accessToken(tenantB);

        String categoryB = createCategory(tokenB, "Orgânicos");
        String productB = createProduct(tokenB, categoryB, "Tomate", "8.90", "KG");

        mockMvc.perform(get("/api/v1/categories/" + categoryB)
                        .header("Authorization", AuthApi.bearer(tokenA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products/" + productB)
                        .header("Authorization", AuthApi.bearer(tokenA)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/v1/products/" + productB)
                        .header("Authorization", AuthApi.bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invasão","price":1.00}
                                """))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/products")
                        .header("Authorization", AuthApi.bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
        mockMvc.perform(get("/api/v1/store/" + AuthApi.establishmentSlug(tenantA) + "/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(0)));
        mockMvc.perform(get("/api/v1/store/" + AuthApi.establishmentSlug(tenantB) + "/catalog"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.products", hasSize(1)))
                .andExpect(jsonPath("$.products[0].name").value("Tomate"));
        mockMvc.perform(get("/api/v1/store/" + AuthApi.establishmentSlug(tenantA) + "/products/" + productB))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/v1/store/" + AuthApi.establishmentSlug(tenantA) + "/cart/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items":[{"productId":"%s","quantity":1}]}
                                """.formatted(productB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].issue").value("Produto indisponível"))
                .andExpect(jsonPath("$.subtotal").value(0));
    }

    @Test
    void cannotAttachProductToAnotherTenantCategory() throws Exception {
        MvcResult tenantA = AuthApi.register(mockMvc, "Loja Cat A", "Lia", AuthApi.uniqueEmail("attach-a"), "senha12345");
        MvcResult tenantB = AuthApi.register(mockMvc, "Loja Cat B", "Leo", AuthApi.uniqueEmail("attach-b"), "senha12345");
        String categoryB = createCategory(AuthApi.accessToken(tenantB), "Do outro");

        mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(tenantA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"%s",
                                  "name":"Produto estranho",
                                  "price":9.90,
                                  "unit":"UN"
                                }
                                """.formatted(categoryB)))
                .andExpect(status().isNotFound());
    }
}
