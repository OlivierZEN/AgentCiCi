package com.codehouse.ciciassistant.agent;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
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
class SkillDagHttpSecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRequireAuthenticationForBothGraphSurfaces() throws Exception {
        mockMvc.perform(get("/agents/cici-system/skill-dag"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));

        mockMvc.perform(get("/platform/skills/1/dependency-graph"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    void shouldKeepCompanyAndPlatformGraphSurfacesIsolated() throws Exception {
        String companyToken = companyToken();
        String platformToken = platformToken();

        mockMvc.perform(get("/agents/cici-system/skill-dag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope.id").value("cici-system"));

        mockMvc.perform(get("/platform/skills/1/dependency-graph")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + companyToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("需要平台账号权限"));

        mockMvc.perform(get("/agents/cici-system/skill-dag")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("需要组织账号权限"));

        mockMvc.perform(get("/platform/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk());
        MvcResult skillsResult = mockMvc.perform(get("/platform/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isNotEmpty())
                .andReturn();
        long skillId = objectMapper.readTree(skillsResult.getResponse().getContentAsString())
                .path("data")
                .path(0)
                .path("id")
                .asLong();

        mockMvc.perform(get("/platform/skills/{skillId}/dependency-graph", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.scope.type").value("SKILL_IMPACT"));
    }

    private String companyToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "demo-org",
                                  "mobile": "13800138111",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String platformToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/platform/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "admin@cloudcc.com",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
