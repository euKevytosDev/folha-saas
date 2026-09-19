package com.sacolao.auth;

import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@TestPropertySource(properties = {
        "app.security.rate-limit.login-max-attempts=3",
        "app.security.rate-limit.login-window-seconds=300"
})
class AuthRateLimitIT extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void loginIsRateLimitedAfterTooManyAttempts() throws Exception {
        String email = AuthApi.uniqueEmail("ratelimit");
        AuthApi.register(mockMvc, "Loja RL", "Owner", email, "senha12345");

        String payload = """
                {"email":"%s","password":"senha-errada"}
                """.formatted(email);

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(payload))
                    .andExpect(status().isUnauthorized());
        }

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(payload))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));
    }
}
