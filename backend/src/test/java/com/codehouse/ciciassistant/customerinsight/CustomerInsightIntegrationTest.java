package com.codehouse.ciciassistant.customerinsight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.customerinsight.domain.CustomerInsightProjectRepository;
import com.codehouse.ciciassistant.customerinsight.domain.CustomerInsightSectionRepository;
import com.codehouse.ciciassistant.skill.domain.SkillDefinitionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.profiles.active=default")
class CustomerInsightIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private CustomerInsightProjectRepository projectRepository;

    @Autowired
    private CustomerInsightSectionRepository sectionRepository;

    @Autowired
    private AgentRunTraceRepository traceRepository;

    @Autowired
    private SkillDefinitionRepository skillDefinitionRepository;

    @Test
    void shouldCreateEditGenerateAndTraceCustomerInsightProject() throws Exception {
        forceMockChatModel();
        String token = loginToken("13900009999");
        String customerName = "测试客户-" + System.currentTimeMillis();

        mockMvc.perform(get("/ai/customer-insights/catalog")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].sectionCode").value("customer_info"))
                .andExpect(jsonPath("$.data[0].groupLabel").value("客户画像"))
                .andExpect(jsonPath("$.data[19].sectionCode").value("signed_contracts"))
                .andExpect(jsonPath("$.data[19].groupLabel").value("业务闭环"));

        mockMvc.perform(get("/ai/customer-insights/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceMode").value("MOCK"))
                .andExpect(jsonPath("$.data.summary.totalLeads").isNumber())
                .andExpect(jsonPath("$.data.funnel[0].label").value("潜在客户"))
                .andExpect(jsonPath("$.data.accounts[0].accountName").isString());

        MvcResult createResult = mockMvc.perform(post("/ai/customer-insights/projects")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "%s",
                                  "industry": "企业服务",
                                  "sourceType": "MANUAL"
                                }
                                """.formatted(customerName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.projectId").value(org.hamcrest.Matchers.startsWith("ci_")))
                .andExpect(jsonPath("$.data.sections[0].sectionCode").value("customer_info"))
                .andReturn();
        JsonNode created = objectMapper.readTree(createResult.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
        String projectId = created.path("projectId").asText();

        mockMvc.perform(patch("/ai/customer-insights/projects/{projectId}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "customerName": "%s",
                                  "customerExternalId": "001xx000001",
                                  "customerObjectApiName": "Account",
                                  "industry": "智能制造",
                                  "sourceType": "CLOUDCC"
                                }
                                """.formatted(customerName)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customerExternalId").value("001xx000001"))
                .andExpect(jsonPath("$.data.industry").value("智能制造"));

        mockMvc.perform(post("/ai/customer-insights/projects/{projectId}/refresh-sources", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.snapshot.sourceType").value("CLOUDCC_CUSTOMER"))
                .andExpect(jsonPath("$.data.snapshots[1].sourceType").value("BUSINESS_CONTRACT"))
                .andExpect(jsonPath("$.data.snapshots[2].sourceType").value("BUSINESS_ORDER"))
                .andExpect(jsonPath("$.data.snapshots[3].sourceType").value("CUSTOMER_SERVICE"));

        mockMvc.perform(put("/ai/customer-insights/projects/{projectId}/sections/customer_info", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": {
                                    "knownFacts": "华东大区重点制造业客户",
                                    "currentOpportunity": "售后服务中台升级"
                                  },
                                  "markdown": "人工补充：已有售后服务中台升级需求。"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DRAFT"));

        MvcResult generateResult = mockMvc.perform(post("/ai/customer-insights/projects/{projectId}/sections/customer_info/generate", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "input": {
                                    "knownFacts": "客户希望提升售后响应效率",
                                    "stakeholders": "信息化负责人、客服负责人"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.success").value(true))
                .andExpect(jsonPath("$.data.section.status").value("GENERATED"))
                .andExpect(jsonPath("$.data.section.skillCode").value("ai-customer-insight-analyst"))
                .andExpect(jsonPath("$.data.section.markdown").value(org.hamcrest.Matchers.containsString("AI 生成")))
                .andExpect(jsonPath("$.data.section.traceId").isString())
                .andReturn();
        String traceId = objectMapper.readTree(generateResult.getResponse().getContentAsString(StandardCharsets.UTF_8))
                .path("data")
                .path("section")
                .path("traceId")
                .asText();

        assertThat(traceRepository.findByTraceIdAndOrgId(traceId, "demo-org")).isPresent();
        assertThat(skillDefinitionRepository.findByOrgIdAndSkillCode("demo-org", "ai-customer-insight-analyst")).isPresent();

        mockMvc.perform(get("/ai/customer-insights/projects/{projectId}", projectId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.generatedSectionCount").value(1))
                .andExpect(jsonPath("$.data.sources[*].sourceLabel").value(org.hamcrest.Matchers.hasItem(customerName)))
                .andExpect(jsonPath("$.data.jobs[0].status").value("SUCCESS"));

        assertThat(projectRepository.findByOrgIdAndPublicId("demo-org", projectId)).isPresent();
        assertThat(sectionRepository.findByProjectIdOrderByIdAsc(projectRepository.findByOrgIdAndPublicId("demo-org", projectId).orElseThrow().getId()))
                .hasSizeGreaterThanOrEqualTo(26);
    }

    private void forceMockChatModel() {
        jdbcTemplate.update("DELETE FROM org_model_config WHERE org_id = ? AND scene_code = ?", "demo-org", "chat");
        jdbcTemplate.update("""
                INSERT INTO org_model_config(org_id, scene_code, provider, model_name)
                VALUES (?, ?, ?, ?)
                """, "demo-org", "chat", "mock", "cici-default");
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andReturn();
        if (loginResult.getResponse().getStatus() != 200) {
            throw new IllegalStateException("login failed: " + loginResult.getResponse().getContentAsString());
        }
        JsonNode data = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        return data.path("token").asText();
    }
}
