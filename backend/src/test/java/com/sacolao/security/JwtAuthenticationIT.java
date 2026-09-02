package com.sacolao.security;

import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import com.sacolao.tenant.TenantContext;
import com.sacolao.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class JwtAuthenticationIT extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void validJwtAllowsAccess() throws Exception {
        String email = AuthApi.uniqueEmail("jwt-ok");
        MvcResult registered = AuthApi.register(mockMvc, "Loja JWT", "Gabi", email, "senha12345");
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(registered))))
                .andExpect(status().isOk());
    }

    @Test
    void expiredJwtIsRejected() throws Exception {
        String email = AuthApi.uniqueEmail("jwt-exp");
        AuthApi.register(mockMvc, "Loja Expirado", "Hugo", email, "senha12345");
        var user = userRepository.findWithEstablishmentByEmail(email).orElseThrow();
        String expired = jwtService.issueAccessToken(user, Duration.ofSeconds(-10));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", AuthApi.bearer(expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void invalidJwtIsRejected() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer not-a-valid-token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tenantContextIsClearedAfterRequest() throws Exception {
        String email = AuthApi.uniqueEmail("jwt-ctx");
        MvcResult registered = AuthApi.register(mockMvc, "Loja Contexto", "Iris", email, "senha12345");
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(registered))))
                .andExpect(status().isOk());
        assertThat(TenantContext.get()).isEmpty();
    }
}
