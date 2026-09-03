package com.sacolao.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

public final class AuthApi {

    private AuthApi() {
    }

    public static String uniqueEmail(String prefix) {
        return prefix + "-" + UUID.randomUUID() + "@folha.test";
    }

    public static MvcResult register(
            MockMvc mockMvc,
            String establishmentName,
            String name,
            String email,
            String password
    ) throws Exception {
        String json = """
                {
                  "establishmentName": "%s",
                  "name": "%s",
                  "email": "%s",
                  "password": "%s"
                }
                """.formatted(establishmentName, name, email, password);
        return mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated())
                .andReturn();
    }

    public static MvcResult login(MockMvc mockMvc, String email, String password) throws Exception {
        String json = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        return mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();
    }

    public static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    public static String refreshToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.refreshToken");
    }

    public static String userId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.user.id");
    }

    public static String establishmentId(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.user.establishmentId");
    }

    public static String establishmentSlug(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.establishment.slug");
    }

    public static String read(MvcResult result, String path) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), path);
    }

    public static String bearer(String accessToken) {
        return "Bearer " + accessToken;
    }
}
