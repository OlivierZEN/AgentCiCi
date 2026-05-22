package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.auth.service.JwtService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class PlatformAuthIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Test
    void shouldLoginPlatformAccountByEmailAndMobileWithoutOrganization() throws Exception {
        MvcResult emailLogin = platformLogin("admin@cloudcc.com")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.platformAccountId").isNotEmpty())
                .andExpect(jsonPath("$.data.email").value("admin@cloudcc.com"))
                .andExpect(jsonPath("$.data.mobile").value("18611892001"))
                .andExpect(jsonPath("$.data.displayName").value("CloudCC Platform Admin"))
                .andExpect(jsonPath("$.data.tokenType").value("platform"))
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").exists())
                .andExpect(jsonPath("$.data.orgId").doesNotExist())
                .andExpect(jsonPath("$.data.orgName").doesNotExist())
                .andExpect(jsonPath("$.data.userId").doesNotExist())
                .andExpect(jsonPath("$.data.memberId").doesNotExist())
                .andExpect(jsonPath("$.data.accountId").doesNotExist())
                .andReturn();

        JsonNode emailData = objectMapper.readTree(emailLogin.getResponse().getContentAsString()).path("data");
        Claims claims = jwtService.parse(emailData.path("token").asText());
        assertThat(claims.get("typ", String.class)).isEqualTo("platform");
        assertThat(claims.get("platform_account_id", String.class)).isEqualTo(emailData.path("platformAccountId").asText());
        assertThat(claims.get("org_id", String.class)).isNull();
        assertThat(claims.get("member_id", String.class)).isNull();
        assertThat(claims.get("account_id", String.class)).isNull();

        MvcResult mobileLogin = platformLogin("18611892001")
                .andExpect(status().isOk())
                .andReturn();
        JsonNode mobileData = objectMapper.readTree(mobileLogin.getResponse().getContentAsString()).path("data");
        assertThat(mobileData.path("platformAccountId").asText()).isEqualTo(emailData.path("platformAccountId").asText());

        Long userAccounts = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_account
                WHERE primary_mobile = '18611892001' OR LOWER(COALESCE(email, '')) = 'admin@cloudcc.com'
                """, Long.class);
        assertThat(userAccounts).isZero();

        Long members = jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM organization_member m
                JOIN user_account a ON a.id = m.account_id
                WHERE a.primary_mobile = '18611892001' OR LOWER(COALESCE(a.email, '')) = 'admin@cloudcc.com'
                """, Long.class);
        assertThat(members).isZero();
    }

    @Test
    void shouldAllowPlatformTokenOnlyOnPlatformSurfaces() throws Exception {
        String platformToken = platformToken("admin@cloudcc.com");

        mockMvc.perform(get("/auth/platform/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("platform"))
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").exists())
                .andExpect(jsonPath("$.data.orgId").doesNotExist());

        mockMvc.perform(get("/platform/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").exists())
                .andExpect(jsonPath("$.data.orgId").doesNotExist());

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/admin/users")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectOrganizationTokensFromPlatformSurfaces() throws Exception {
        MvcResult orgLogin = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138111",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[?(@ == 'PLATFORM_ADMIN')]").doesNotExist())
                .andReturn();
        String orgToken = objectMapper.readTree(orgLogin.getResponse().getContentAsString()).path("data").path("token").asText();

        mockMvc.perform(get("/platform/bootstrap")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void shouldRejectNonPlatformAccountLogin() throws Exception {
        platformLogin("13900009999")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid account or password"));
    }

    private org.springframework.test.web.servlet.ResultActions platformLogin(String identifier) throws Exception {
        return mockMvc.perform(post("/auth/platform/password/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {
                          "identifier": "%s",
                          "password": "szyd1234"
                        }
                        """.formatted(identifier)));
    }

    private String platformToken(String identifier) throws Exception {
        MvcResult result = platformLogin(identifier)
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
