package com.sacolao.auth;

import com.sacolao.auth.token.RefreshToken;
import com.sacolao.auth.token.RefreshTokenRepository;
import com.sacolao.auth.token.TokenHash;
import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import com.sacolao.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class RefreshTokenIT extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Test
    void expiredRefreshTokenIsRejected() throws Exception {
        String email = AuthApi.uniqueEmail("refresh-exp");
        AuthApi.register(mockMvc, "Loja Refresh Exp", "Nico", email, "senha12345");
        var user = userRepository.findByEmail(email).orElseThrow();
        String raw = "expired-refresh-" + email;
        RefreshToken token = new RefreshToken();
        token.setUser(user);
        token.setTokenHash(TokenHash.sha256(raw));
        token.setExpiresAt(Instant.now().minus(1, ChronoUnit.MINUTES));
        refreshTokenRepository.save(token);

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(raw)))
                .andExpect(status().isUnauthorized());
    }
}
