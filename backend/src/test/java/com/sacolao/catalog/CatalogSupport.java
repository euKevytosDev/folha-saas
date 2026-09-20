package com.sacolao.catalog;

import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
        return createProduct(token, categoryId, name, price, unit, null);
    }

    protected String createProduct(
            String token,
            String categoryId,
            String name,
            String price,
            String unit,
            String imageUrl
    ) throws Exception {
        String imageField = imageUrl == null || imageUrl.isBlank()
                ? ""
                : ",\"imageUrl\":\"" + imageUrl + "\"";
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
                                  %s
                                }
                                """.formatted(categoryId, name, price, unit, imageField)))
                .andExpect(status().isCreated())
                .andReturn();
        return AuthApi.read(result, "$.id");
    }

    protected void forceStoreOpen(String token, String establishmentId) throws Exception {
        mockMvc.perform(put("/api/v1/establishments/" + establishmentId)
                        .header("Authorization", AuthApi.bearer(token))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"storeOpenMode":"OPEN"}
                                """))
                .andExpect(status().isOk());
    }
}
