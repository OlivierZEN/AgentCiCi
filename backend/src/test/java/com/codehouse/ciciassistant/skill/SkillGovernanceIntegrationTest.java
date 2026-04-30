package com.codehouse.ciciassistant.skill;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
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
class SkillGovernanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldHidePlatformCoreSkillsFromTenantListAndAllowDerive() throws Exception {
        String token = loginToken("13800138111");

        MvcResult listResult = mockMvc.perform(get("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode skills = objectMapper.readTree(listResult.getResponse().getContentAsString()).path("data");
        assertThat(skills.isArray()).isTrue();
        assertThat(skills).extracting(node -> node.path("skillCode").asText())
                .doesNotContain("conversation-core", "knowledge-first", "safe-handoff")
                .contains("general-assistant");

        JsonNode generalAssistant = null;
        for (JsonNode node : skills) {
            if ("general-assistant".equals(node.path("skillCode").asText())) {
                generalAssistant = node;
                break;
            }
        }
        assertThat(generalAssistant).isNotNull();

        mockMvc.perform(put("/skills/{id}", generalAssistant.path("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "general-assistant",
                                  "name": "通用助手（租户改名尝试）",
                                  "description": "should be ignored",
                                  "enabled": false,
                                  "riskLevel": "HIGH"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false))
                .andExpect(jsonPath("$.data.name").value("通用助手"))
                .andExpect(jsonPath("$.data.editPolicy").value("CONFIGURABLE"));

        mockMvc.perform(post("/skills/{id}/derive", generalAssistant.path("id").asLong())
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "skillCode": "general-assistant-derived",
                                  "name": "通用助手派生版"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceType").value("TENANT_DERIVED"))
                .andExpect(jsonPath("$.data.templateCode").value("general-assistant"))
                .andExpect(jsonPath("$.data.baseTemplateVersion").value(1));
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode send = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        String code = send.path("data").path("devCode").asText();

        MvcResult loginResult = mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "code": "%s"
                                }
                                """.formatted(mobile, code)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
