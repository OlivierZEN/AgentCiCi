package com.codehouse.ciciassistant.organization;

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
class AdminOrganizationProfileIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void ownerCanReadAndUpdateOrganizationProfileWithoutChangingOrgId() throws Exception {
        CreatedOrg created = registerOrg("组织设置测试组织");

        mockMvc.perform(get("/admin/organization/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + created.token()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgId").value(created.orgId()))
                .andExpect(jsonPath("$.data.name").value("组织设置测试组织"))
                .andExpect(jsonPath("$.data.owner.displayName").isNotEmpty())
                .andExpect(jsonPath("$.data.memberCount").value(1));

        mockMvc.perform(patch("/admin/organization/profile")
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
                                  "organizationSize": "51-200",
                                  "timezone": "Asia/Shanghai",
                                  "notes": "内部支持备注"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgId").value(created.orgId()))
                .andExpect(jsonPath("$.data.name").value("鎏金账房科技"))
                .andExpect(jsonPath("$.data.shortName").value("账房科技"))
                .andExpect(jsonPath("$.data.contactEmail").value("ops@example.com"));

        assertThat(jdbcTemplate.queryForObject("SELECT name FROM org WHERE id = ?", String.class, created.orgId()))
                .isEqualTo("鎏金账房科技");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT contact_email FROM organization_profile WHERE org_id = ?",
                String.class,
                created.orgId()))
                .isEqualTo("ops@example.com");
        Long auditCount = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM audit_log WHERE org_id = ? AND event_type = 'organization.name.update'",
                Long.class,
                created.orgId());
        assertThat(auditCount).isEqualTo(1);

        mockMvc.perform(patch("/admin/organization/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + created.token())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "other-org",
                                  "name": "不能改系统标识"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("组织 ID 是系统标识，不允许修改"));

        assertThat(jdbcTemplate.queryForObject("SELECT id FROM org WHERE id = ?", String.class, created.orgId()))
                .isEqualTo(created.orgId());
    }

    @Test
    void orgUserCannotUpdateOrganizationProfile() throws Exception {
        CreatedOrg created = registerOrg("组织权限测试组织");
        String userToken = loginOrgUser(created.orgId());

        mockMvc.perform(patch("/admin/organization/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "普通成员不应保存"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    private CreatedOrg registerOrg(String organizationName) throws Exception {
        String mobile = randomMobile();
        MvcResult result = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "%s"
                                }
                                """.formatted(mobile, organizationName)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode data = objectMapper.readTree(result.getResponse().getContentAsString()).path("data");
        return new CreatedOrg(data.path("orgId").asText(), data.path("token").asText());
    }

    private String loginOrgUser(String orgId) throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "%s",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(orgId, randomMobile())))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private static String randomMobile() {
        int suffix = Math.floorMod(UUID.randomUUID().hashCode(), 100_000_000);
        return "139" + String.format("%08d", suffix);
    }

    private record CreatedOrg(String orgId, String token) {
    }
}
