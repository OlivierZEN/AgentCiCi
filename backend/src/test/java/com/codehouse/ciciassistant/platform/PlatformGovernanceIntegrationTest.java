package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.ai.service.ToolOrchestratorService;
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
class PlatformGovernanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ToolOrchestratorService toolOrchestratorService;

    @Test
    void shouldManagePlatformSkillTemplateVersionsAndBuiltinToolsWithAuditAndRuntimeKillSwitch() throws Exception {
        String platformToken = loginToken("13800138111");

        MvcResult platformSkillsResult = mockMvc.perform(get("/platform/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode platformSkills = objectMapper.readTree(platformSkillsResult.getResponse().getContentAsString()).path("data");
        JsonNode generalAssistant = findByField(platformSkills, "skillCode", "general-assistant");
        assertThat(generalAssistant).isNotNull();
        assertThat(generalAssistant.path("impact").path("boundAgentCount").asInt()).isGreaterThanOrEqualTo(0);

        long skillId = generalAssistant.path("id").asLong();

        mockMvc.perform(post("/platform/skills/{id}/versions", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "通用助手平台版 v2",
                                  "description": "平台收口后的可写模板版本",
                                  "promptFragment": "Act as the managed platform assistant. Prefer clear decisions and grounded answers.",
                                  "toolWhitelist": ["tavily_search", "tavily_extract"],
                                  "kbWhitelist": [],
                                  "handoffRule": "当信息不足或涉及高风险动作时转人工。",
                                  "outputContract": "先结论，后依据与下一步。",
                                  "riskLevel": "LOW",
                                  "changelog": "新增 Web 搜索与平台运营版输出规范"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestDraftVersionNo").value(2));

        mockMvc.perform(post("/platform/skills/{id}/publish", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": 2,
                                  "enabled": true,
                                  "visibility": "VISIBLE",
                                  "bindingPolicy": "DEFAULT_ON"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentTemplateVersionNo").value(2))
                .andExpect(jsonPath("$.data.bindingPolicy").value("DEFAULT_ON"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        mockMvc.perform(put("/platform/tools/{toolName}", "email_send")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Email Send Kill Switch",
                                  "description": "平台治理后的高风险邮件发送紧急禁用入口。",
                                  "riskLevel": "HIGH",
                                  "category": "email",
                                  "enabled": false
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.displayName").value("Email Send Kill Switch"))
                .andExpect(jsonPath("$.data.enabled").value(false));

        MvcResult toolsResult = mockMvc.perform(get("/platform/tools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tools = objectMapper.readTree(toolsResult.getResponse().getContentAsString()).path("data");
        JsonNode emailSend = findByField(tools, "toolName", "email_send");
        assertThat(emailSend).isNotNull();
        assertThat(emailSend.path("displayName").asText()).isEqualTo("Email Send Kill Switch");
        assertThat(emailSend.path("riskLevel").asText()).isEqualTo("HIGH");
        assertThat(emailSend.path("enabled").asBoolean()).isFalse();

        String blocked = toolOrchestratorService.executeTool(
                "demo-org",
                "platform-test-user",
                "email_send",
                "{\"to\":\"ops@example.com\",\"subject\":\"test\",\"body\":\"hello\"}",
                java.util.List.of("email_send"),
                java.util.List.of("email_send")
        );
        assertThat(blocked).isEqualTo("Tool is disabled by platform runtime control: email_send");

        MvcResult policyResult = mockMvc.perform(get("/platform/policies/core")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode policy = objectMapper.readTree(policyResult.getResponse().getContentAsString()).path("data");
        assertThat(policy.path("bundleCode").asText()).isEqualTo("core-default");
        assertThat(policy.path("sourceSkillCodes")).extracting(JsonNode::asText)
                .contains("conversation-core", "knowledge-first", "safe-handoff");
        assertThat(policy.path("versionCount").asInt()).isGreaterThanOrEqualTo(1);

        mockMvc.perform(post("/platform/policies/core/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Platform Core Policy Bundle v2",
                                  "description": "加强高风险动作确认与知识优先策略说明。",
                                  "promptFragment": "Always enforce platform core safety. Require grounded answers and explicit confirmation before high-risk actions.",
                                  "handoffRules": ["高风险动作必须再次确认。", "无依据时转人工。"],
                                  "sourceSkillCodes": ["conversation-core", "knowledge-first", "safe-handoff"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.latestDraftVersionNo").value(2));

        MvcResult policyVersionsResult = mockMvc.perform(get("/platform/policies/core/versions")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode policyVersions = objectMapper.readTree(policyVersionsResult.getResponse().getContentAsString()).path("data");
        assertThat(policyVersions.isArray()).isTrue();
        assertThat(policyVersions.get(0).path("versionNo").asInt()).isEqualTo(2);
        assertThat(policyVersions.get(0).path("impact").path("rolloutStage").asText()).isEqualTo("DRAFT_PENDING");

        mockMvc.perform(post("/platform/policies/core/publish")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": 2
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(2));

        mockMvc.perform(post("/platform/policies/core/rollback")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "versionNo": 1
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.versionNo").value(1));

        MvcResult versionsResult = mockMvc.perform(get("/platform/skills/{id}/versions", skillId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode versions = objectMapper.readTree(versionsResult.getResponse().getContentAsString()).path("data");
        assertThat(versions.isArray()).isTrue();
        assertThat(versions.get(0).path("impact").path("summaryLines").isArray()).isTrue();
        assertThat(versions.get(0).path("impact").path("rolloutStage").asText()).isNotBlank();

        MvcResult auditResult = mockMvc.perform(get("/platform/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode auditRows = objectMapper.readTree(auditResult.getResponse().getContentAsString()).path("data");
        assertThat(auditRows.isArray()).isTrue();
        assertThat(auditRows).extracting(node -> node.path("eventType").asText())
                .contains(
                        "platform.skill.version.create",
                        "platform.skill.publish",
                        "platform.tool.update",
                        "platform.policy.version.create",
                        "platform.policy.publish",
                        "platform.policy.rollback"
                );

        String orgAdminToken = loginToken("13800138188");
        MvcResult tenantToolsResult = mockMvc.perform(get("/tools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tenantTools = objectMapper.readTree(tenantToolsResult.getResponse().getContentAsString()).path("data");
        assertThat(tenantTools).extracting(node -> node.path("toolName").asText())
                .doesNotContain("email_send");
    }

    @Test
    void shouldRejectOrgAdminFromPlatformWriteApis() throws Exception {
        String orgAdminToken = loginToken("13800138188");

        mockMvc.perform(get("/platform/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(put("/platform/tools/{toolName}", "email_send")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgAdminToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "Blocked Update",
                                  "description": "ORG_ADMIN should not mutate platform tools.",
                                  "riskLevel": "LOW",
                                  "category": "web",
                                  "enabled": true
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private JsonNode findByField(JsonNode rows, String field, String expected) {
        if (rows == null || !rows.isArray()) {
            return null;
        }
        for (JsonNode row : rows) {
            if (expected.equals(row.path(field).asText())) {
                return row;
            }
        }
        return null;
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

        String code = objectMapper.readTree(sendResult.getResponse().getContentAsString())
                .path("data")
                .path("devCode")
                .asText();

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
