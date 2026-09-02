package com.sacolao.user;

import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AuthorizationIT extends IntegrationTest {

    private static final String PASSWORD = "senha12345";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void staffCannotManageUsers() throws Exception {
        String ownerEmail = AuthApi.uniqueEmail("staff-owner");
        MvcResult owner = AuthApi.register(mockMvc, "Loja Staff", "Dono", ownerEmail, PASSWORD);
        String staffEmail = AuthApi.uniqueEmail("staff");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Funcionário",
                                  "email": "%s",
                                  "password": "%s",
                                  "role": "STAFF"
                                }
                                """.formatted(staffEmail, PASSWORD)))
                .andExpect(status().isCreated());

        MvcResult staff = AuthApi.login(mockMvc, staffEmail, PASSWORD);
        String staffToken = AuthApi.accessToken(staff);

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", AuthApi.bearer(staffToken)))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", AuthApi.bearer(staffToken))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Outro",
                                  "email": "%s",
                                  "password": "%s",
                                  "role": "STAFF"
                                }
                                """.formatted(AuthApi.uniqueEmail("blocked"), PASSWORD)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/establishments/me")
                        .header("Authorization", AuthApi.bearer(staffToken)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/admin/establishments")
                        .header("Authorization", AuthApi.bearer(staffToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void ownerCannotCreateSuperAdminViaUserApi() throws Exception {
        String email = AuthApi.uniqueEmail("owner-priv");
        MvcResult owner = AuthApi.register(mockMvc, "Loja Priv", "Dono", email, PASSWORD);
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(owner)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Hack",
                                  "email": "%s",
                                  "password": "%s",
                                  "role": "SUPER_ADMIN"
                                }
                                """.formatted(AuthApi.uniqueEmail("hack"), PASSWORD)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_ROLE"));
    }
}
