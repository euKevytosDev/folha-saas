package com.sacolao.catalog;

import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public abstract class CatalogSupport extends IntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    protected String createCategory(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/categories")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"%s","sortOrder":1}
                                """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return AuthApi.read(result, "$.id");
    }

    protected String createProduct(String token, String categoryId, String name, String price, String unit) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/products")
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "categoryId":"%s",
                                  "name":"%s",
                                  "price":%s,
                                  "unit":"%s",
                                  "available":true,
                                  "featured":false,
                                  "minimumQuantity":1
                                }
                                """.formatted(categoryId, name, price, unit)))
                .andExpect(status().isCreated())
                .andReturn();
        return AuthApi.read(result, "$.id");
    }
}
