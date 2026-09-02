package com.sacolao.tenant;

import com.sacolao.security.JwtService;
import com.sacolao.support.AuthApi;
import com.sacolao.support.IntegrationTest;
import com.sacolao.user.entity.User;
import com.sacolao.user.repository.UserRepository;
import com.sacolao.user.service.UserService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class TenantIsolationIT extends IntegrationTest {

    private static final String PASSWORD = "senha12345";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserService userService;

    @Test
    void ownerOfTenantACannotReadOrChangeTenantB() throws Exception {
        String emailA = AuthApi.uniqueEmail("tenant-a");
        String emailB = AuthApi.uniqueEmail("tenant-b");
        MvcResult tenantA = AuthApi.register(mockMvc, "Loja Alfa", "Owner A", emailA, PASSWORD);
        MvcResult tenantB = AuthApi.register(mockMvc, "Loja Beta", "Owner B", emailB, PASSWORD);

        String tokenA = AuthApi.accessToken(tenantA);
        String tokenB = AuthApi.accessToken(tenantB);
        String establishmentB = AuthApi.establishmentId(tenantB);
        String userB = AuthApi.userId(tenantB);

        mockMvc.perform(get("/api/v1/establishments/" + establishmentB)
                        .header("Authorization", AuthApi.bearer(tokenA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(put("/api/v1/establishments/" + establishmentB)
                        .header("Authorization", AuthApi.bearer(tokenA))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Invasão"}
                                """))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/users/" + userB)
                        .header("Authorization", AuthApi.bearer(tokenA)))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", AuthApi.bearer(tokenA)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(emailA));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", AuthApi.bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].email").value(emailB));

        mockMvc.perform(get("/api/v1/establishments/" + establishmentB)
                        .header("Authorization", AuthApi.bearer(tokenB)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(establishmentB));
    }

    @Test
    void adminOfTenantACannotAccessTenantB() throws Exception {
        String ownerAEmail = AuthApi.uniqueEmail("owner-a");
        String ownerBEmail = AuthApi.uniqueEmail("owner-b");
        MvcResult tenantA = AuthApi.register(mockMvc, "Alfa Admin", "Owner A", ownerAEmail, PASSWORD);
        MvcResult tenantB = AuthApi.register(mockMvc, "Beta Admin", "Owner B", ownerBEmail, PASSWORD);
        String adminEmail = AuthApi.uniqueEmail("admin-a");

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(tenantA)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Admin A",
                                  "email": "%s",
                                  "password": "%s",
                                  "role": "ADMIN"
                                }
                                """.formatted(adminEmail, PASSWORD)))
                .andExpect(status().isCreated());

        MvcResult adminLogin = AuthApi.login(mockMvc, adminEmail, PASSWORD);
        mockMvc.perform(get("/api/v1/establishments/" + AuthApi.establishmentId(tenantB))
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(adminLogin))))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/v1/users/" + AuthApi.userId(tenantB))
                        .header("Authorization", AuthApi.bearer(AuthApi.accessToken(adminLogin))))
                .andExpect(status().isNotFound());
    }

    @Test
    void superAdminHasNoTenantAndCannotUseOperationalUserEndpoints() throws Exception {
        User superAdmin = userService.createSuperAdmin(
                "Plataforma",
                AuthApi.uniqueEmail("super"),
                PASSWORD
        );
        String token = jwtService.issueAccessToken(
                userRepository.findWithEstablishmentById(superAdmin.getId()).orElseThrow()
        );

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.role").value("SUPER_ADMIN"));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/establishments/me")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isForbidden());

        MvcResult tenant = AuthApi.register(mockMvc, "Loja Vista", "Owner", AuthApi.uniqueEmail("vista"), PASSWORD);
        mockMvc.perform(get("/api/v1/admin/establishments")
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/v1/establishments/" + AuthApi.establishmentId(tenant))
                        .header("Authorization", AuthApi.bearer(token)))
                .andExpect(status().isOk());
    }
}
