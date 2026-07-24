package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class RbacProductionReadinessIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldRejectProtectedBusinessApisWithoutTokenOrForgedHeaderContext() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get("/me/email-accounts")
                        .header("X-Company-Id", "demo-org")
                        .header("X-User-Id", "forged-user"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldKeepExplicitPublicAndExternalAuthEntrypointsReachable() throws Exception {
        mockMvc.perform(get("/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("UP"));

        mockMvc.perform(get("/actuator/health"))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isNotIn(401, 403))
                .andExpect(jsonPath("$.status").exists());

        mockMvc.perform(get("/public/agents/avatars")
                        .queryParam("companyId", "demo-org"))
                .andExpect(status().isOk());

        mockMvc.perform(get("/openapi/v1/parameters"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("agent_api_key_missing"));

        mockMvc.perform(post("/embed/v1/apps/meeting-minutes/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Embed token is required"));
    }

    @Test
    void shouldKeepCompanyAndPlatformTokensIsolated() throws Exception {
        String orgAdminToken = orgToken("13902406401");
        String platformToken = platformAdminToken();

        mockMvc.perform(get("/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("需要组织管理员权限"));

        mockMvc.perform(get("/platform/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("需要平台账号权限"));
    }

    @Test
    void shouldRejectOrgUserFromOrgAdminEndpoints() throws Exception {
        String orgUserToken = orgToken("13902406402");

        mockMvc.perform(get("/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgUserToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("需要组织管理员权限"));
    }

    @Test
    void shouldAllowPlatformAuditorReadButRejectPlatformWrites() throws Exception {
        String auditorToken = jwtService.issuePlatformToken("platform-auditor-test", List.of(RoleCodes.PLATFORM_AUDITOR));

        mockMvc.perform(get("/platform/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken))
                .andExpect(status().isOk());

        mockMvc.perform(put("/platform/tools/{toolName}", "email_send")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Auditor Must Not Write",
                                  "description": "Auditor write attempt should be rejected.",
                                  "riskLevel": "LOW",
                                  "category": "email",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("当前平台角色无权访问该资源"));

        mockMvc.perform(put("/platform/models/providers/{providerCode}", "deepseek")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + auditorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "apiBaseUrl": "https://auditor-denied.example.invalid/v1",
                                  "apiKey": "must-not-save"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("当前平台角色无权访问该资源"));
    }

    private String orgToken(String mobile) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "demo-org",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String platformAdminToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/platform/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "admin@cloudcc.com",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
