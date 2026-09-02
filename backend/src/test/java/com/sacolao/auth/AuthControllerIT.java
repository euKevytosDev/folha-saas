package com.sacolao.auth;

import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthControllerIT extends IntegrationTest {

    private static final String PASSWORD = "senha12345";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void registerCreatesEstablishmentAndOwner() throws Exception {
        String email = AuthApi.uniqueEmail("owner");
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "establishmentName": "Sacolão Aurora",
                                  "name": "Ana Owner",
                                  "email": "%s",
                                  "password": "%s",
                                  "role": "SUPER_ADMIN",
                                  "establishmentId": "11111111-1111-1111-1111-111111111111",
                                  "planCode": "PREMIUM"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.user.role").value("OWNER"))
                .andExpect(jsonPath("$.user.email").value(email))
                .andExpect(jsonPath("$.user.establishmentId").isNotEmpty())
                .andExpect(jsonPath("$.establishment.planCode").value("BASIC"))
                .andExpect(jsonPath("$.user.passwordHash").doesNotExist());
    }

    @Test
    void registerRejectsDuplicateEmail() throws Exception {
        String email = AuthApi.uniqueEmail("dup");
        AuthApi.register(mockMvc, "Loja Dup", "Maria", email, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "establishmentName": "Outra Loja",
                                  "name": "João",
                                  "email": "%s",
                                  "password": "%s"
                                }
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void loginSucceedsWithValidCredentials() throws Exception {
        String email = AuthApi.uniqueEmail("login");
        AuthApi.register(mockMvc, "Loja Login", "Carlos", email, PASSWORD);
        AuthApi.login(mockMvc, email, PASSWORD)
                .getResponse();
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(email, PASSWORD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.user.role").value("OWNER"));
    }

    @Test
    void loginFailsWithWrongPassword() throws Exception {
        String email = AuthApi.uniqueEmail("wrong-pass");
        AuthApi.register(mockMvc, "Loja Senha", "Lia", email, PASSWORD);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"outraSenha1"}
                                """.formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }

    @Test
    void loginFailsWithUnknownEmail() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s"}
                                """.formatted(AuthApi.uniqueEmail("ghost"), PASSWORD)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciais inválidas"));
    }

    @Test
    void refreshRotatesTokenAndRejectsPrevious() throws Exception {
        String email = AuthApi.uniqueEmail("refresh");
        MvcResult registered = AuthApi.register(mockMvc, "Loja Refresh", "Rui", email, PASSWORD);
        String firstRefresh = AuthApi.refreshToken(registered);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(firstRefresh)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.refreshToken").value(not(firstRefresh)))
                .andReturn();

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(firstRefresh)))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(refreshed))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(email));
    }

    @Test
    void logoutRevokesRefreshToken() throws Exception {
        String email = AuthApi.uniqueEmail("logout");
        MvcResult registered = AuthApi.register(mockMvc, "Loja Logout", "Eva", email, PASSWORD);
        String refresh = AuthApi.refreshToken(registered);

        mockMvc.perform(post("/api/v1/auth/logout")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isNoContent());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"%s"}
                                """.formatted(refresh)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void meRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isUnauthorized());
    }
}
