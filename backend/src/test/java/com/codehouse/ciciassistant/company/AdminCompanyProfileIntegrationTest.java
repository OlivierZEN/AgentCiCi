package com.codehouse.ciciassistant.company;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
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
@TestPropertySource(properties = {
        "spring.profiles.active=default"
})
class AdminCompanyProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerCanReadAndUpdateCompanyProfileWithoutChangingCompanyId() throws Exception {
        CreatedOrg created = registerOrg("组织设置测试组织");
        seedUsageSummaryData(created.companyId());

        mockMvc.perform(get("/admin/company/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + created.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(created.companyId()))
                .andExpect(jsonPath("$.data.name").value("组织设置测试组织"))
                .andExpect(jsonPath("$.data.owner.displayName").isNotEmpty())
                .andExpect(jsonPath("$.data.memberCount").value(1))
                .andExpect(jsonPath("$.data.usageSummary.activeUserCount").value(1))
                .andExpect(jsonPath("$.data.usageSummary.createdUserCount").value(1))
                .andExpect(jsonPath("$.data.usageSummary.knowledgeBaseCount").value(1))
                .andExpect(jsonPath("$.data.usageSummary.knowledgeDocumentCount").value(1))
                .andExpect(jsonPath("$.data.usageSummary.skillCount").value(1))
                .andExpect(jsonPath("$.data.usageSummary.agentCount").value(2))
                .andExpect(jsonPath("$.data.usageSummary.publishedAgentCount").value(1))
                .andExpect(jsonPath("$.data.usageSummary.exportJobCount").value(1));

        mockMvc.perform(patch("/admin/company/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + created.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "鎏金账房科技",
                                  "shortName": "账房科技",
                                  "contactName": "王女士",
                                  "contactPhone": "13800000000",
                                  "contactEmail": "ops@example.com",
                                  "website": "https://example.com",
                                  "industry": "企业服务",
                                  "companySize": "51-200",
                                  "timezone": "Asia/Shanghai",
                                  "notes": "内部支持备注"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(created.companyId()))
                .andExpect(jsonPath("$.data.name").value("鎏金账房科技"))
                .andExpect(jsonPath("$.data.shortName").value("账房科技"))
                .andExpect(jsonPath("$.data.contactEmail").value("ops@example.com"));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM company WHERE id = ?", String.class, created.companyId()))
                .isEqualTo("鎏金账房科技");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT contact_email FROM company_profile WHERE company_id = ?",
                String.class,
                created.companyId()))
                .isEqualTo("ops@example.com");
        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE company_id = ? AND event_type = 'company.name.update'",
                Long.class,
                created.companyId());
        assertThat(auditCount).isEqualTo(1);

        mockMvc.perform(patch("/admin/company/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + created.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "other-org",
                                  "name": "不能改系统标识"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("组织 ID 是系统标识，不允许修改"));

        assertThat(jdbcTemplate.queryForObject("SELECT id FROM company WHERE id = ?", String.class, created.companyId()))
                .isEqualTo(created.companyId());
    }

    @Test
    void orgUserCannotUpdateCompanyProfile() throws Exception {
        CreatedOrg created = registerOrg("组织权限测试组织");
        String userToken = loginOrgUser(created.companyId());

        mockMvc.perform(patch("/admin/company/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "普通成员不应保存"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private CreatedOrg registerOrg(String companyName) throws Exception {
        String mobile = randomMobile();
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "companyName": "%s"
                                }
                                """.formatted(mobile, companyName)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new CreatedOrg(data.path("companyId").asText(), data.path("token").asText());
    }

    private String loginOrgUser(String companyId) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "%s",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(companyId, randomMobile())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private void seedUsageSummaryData(String companyId) {
        jdbcTemplate.update("""
                INSERT INTO knowledge_base(company_id, name, description, status, created_at, updated_at)
                VALUES (?, '组织简档统计知识库', 'usage summary fixture', 'ACTIVE', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, companyId);
        Long kbId = jdbcTemplate.queryForObject(
                "SELECT id FROM knowledge_base WHERE company_id = ? AND name = '组织简档统计知识库'",
                Long.class,
                companyId);
        jdbcTemplate.update("""
                INSERT INTO kb_document(company_id, knowledge_base_id, name, content_type, storage_path, status, created_at, updated_at)
                VALUES (?, ?, '组织简档统计文档', 'text/plain', '/tmp/usage-summary.txt', 'PUBLISHED', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, companyId, kbId);
        jdbcTemplate.update("""
                INSERT INTO skill_definition(
                    company_id, skill_code, name, description, builtin, enabled, prompt_fragment,
                    tool_whitelist, kb_whitelist, handoff_rule, output_contract, risk_level, created_at, updated_at
                )
                VALUES (?, 'usage-summary-skill', '统计技能', 'usage summary fixture', FALSE, TRUE, '',
                    '', '', '', '', 'LOW', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, companyId);
        jdbcTemplate.update("""
                INSERT INTO agent_definition(
                    company_id, agent_id, name, summary, greeting, model, system_prompt, handoff_rule,
                    safety_level, execution_mode, version_label, builtin, enabled, published_version_id, created_at, updated_at
                )
                VALUES (?, 'usage-summary-agent-published', '已发布统计智能体', '', '', 'demo-model', '', '',
                    'LOW', 'CHAT', 'v1', FALSE, TRUE, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, companyId);
        jdbcTemplate.update("""
                INSERT INTO agent_definition(
                    company_id, agent_id, name, summary, greeting, model, system_prompt, handoff_rule,
                    safety_level, execution_mode, version_label, builtin, enabled, published_version_id, created_at, updated_at
                )
                VALUES (?, 'usage-summary-agent-draft', '草稿统计智能体', '', '', 'demo-model', '', '',
                    'LOW', 'CHAT', 'draft', FALSE, TRUE, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, companyId);
        jdbcTemplate.update("""
                INSERT INTO company_export_job(company_id, status, requested_by, reason, manifest_json, created_at, updated_at)
                VALUES (?, 'SUCCEEDED', 'test-owner', 'usage summary fixture', '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, companyId);
    }

    private static String randomMobile() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        return "139" + String.format("%08d", suffix);
    }

    private record CreatedOrg(String companyId, String token) {
    }
}
