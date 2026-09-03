package com.sacolao.auth;

import com.sacolao.auth.token.PasswordResetToken;
import com.sacolao.auth.token.PasswordResetTokenRepository;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class PasswordResetIT extends IntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void forgotPasswordAlwaysReturnsGenericMessage() throws Exception {
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(AuthApi.uniqueEmail("unknown"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Se o e-mail estiver cadastrado, enviaremos instruções para recuperação."));
    }

    @Test
    void forgotPasswordPersistsOnlyTheTokenHash() throws Exception {
        String email = AuthApi.uniqueEmail("forgot-hash");
        AuthApi.register(mockMvc, "Loja Forgot", "Nara", email, "senha12345");
        var user = userRepository.findByEmail(email).orElseThrow();

        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s"}
                                """.formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(
                        "Se o e-mail estiver cadastrado, enviaremos instruções para recuperação."))
                .andExpect(jsonPath("$.token").doesNotExist());

        assertThat(passwordResetTokenRepository.countByUser_Id(user.getId())).isEqualTo(1);
        PasswordResetToken stored = passwordResetTokenRepository.findByUser_Id(user.getId()).getFirst();
        assertThat(stored.getTokenHash()).hasSize(64);
        assertThat(stored.getTokenHash()).matches("[0-9a-f]{64}");
    }

    @Test
    void resetPasswordWithValidTokenUpdatesPassword() throws Exception {
        String email = AuthApi.uniqueEmail("reset");
        AuthApi.register(mockMvc, "Loja Reset", "Paula", email, "senhaAntiga1");
        var user = userRepository.findByEmail(email).orElseThrow();
        String rawToken = "reset-token-" + email;
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(TokenHash.sha256(rawToken));
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        passwordResetTokenRepository.save(token);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"senhaNova12"}
                                """.formatted(rawToken)))
                .andExpect(status().isOk());

        AuthApi.login(mockMvc, email, "senhaNova12");
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"senhaAntiga1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPasswordRejectsExpiredToken() throws Exception {
        String email = AuthApi.uniqueEmail("reset-exp");
        AuthApi.register(mockMvc, "Loja Reset Exp", "Pedro", email, "senha12345");
        var user = userRepository.findByEmail(email).orElseThrow();
        String rawToken = "expired-token-" + email;
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(TokenHash.sha256(rawToken));
        token.setExpiresAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        passwordResetTokenRepository.save(token);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"senhaNova12"}
                                """.formatted(rawToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void resetPasswordTokenIsSingleUse() throws Exception {
        String email = AuthApi.uniqueEmail("reset-once");
        AuthApi.register(mockMvc, "Loja Reset Once", "Pia", email, "senha12345");
        var user = userRepository.findByEmail(email).orElseThrow();
        String rawToken = "once-token-" + email;
        PasswordResetToken token = new PasswordResetToken();
        token.setUser(user);
        token.setTokenHash(TokenHash.sha256(rawToken));
        token.setExpiresAt(Instant.now().plus(1, ChronoUnit.HOURS));
        passwordResetTokenRepository.save(token);

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"senhaNova12"}
                                """.formatted(rawToken)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","password":"outraSenha9"}
                                """.formatted(rawToken)))
                .andExpect(status().isUnauthorized());
    }
}
