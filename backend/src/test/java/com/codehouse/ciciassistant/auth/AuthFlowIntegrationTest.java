package com.codehouse.ciciassistant.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.auth.domain.OrgRepository;
import com.codehouse.ciciassistant.auth.domain.UserEntity;
import com.codehouse.ciciassistant.auth.domain.UserRepository;
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
class AuthFlowIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private OrgRepository orgRepository;

    @Autowired
    private UserRepository userRepository;

    @Test
    void shouldLoginBySmsAndReadCurrentUser() throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138000"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sendJson = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        String code = sendJson.path("data").path("devCode").asText();
        assertThat(code).hasSize(6);

        MvcResult loginResult = mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138000",
                                  "code": "%s"
                                }
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String token = loginJson.path("data").path("token").asText();
        assertThat(token).isNotBlank();

        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void shouldRejectWrongCode() throws Exception {
        mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138001"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138001",
                                  "code": "000000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Invalid verification code"));
    }

    @Test
    void shouldRejectLoginWithoutCodeIssued() throws Exception {
        mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138002",
                                  "code": "123456"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Verification code expired or missing"));
    }

    @Test
    void shouldRejectFrequentSmsRequests() throws Exception {
        mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138003"
                                }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138003"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("SMS request too frequent, please retry later"));
    }

    @Test
    void shouldRejectInvalidToken() throws Exception {
        mockMvc.perform(get("/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void shouldPromoteExistingOrgUserToAdminWhenMobileInBootstrapList() throws Exception {
        var org = orgRepository.findById("demo-org").orElseThrow();
        userRepository.save(new UserEntity(org, "13800138188", RoleCodes.ORG_USER));

        MvcResult sendResult = mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138188"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode sendJson = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        String code = sendJson.path("data").path("devCode").asText();

        mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138188",
                                  "code": "%s"
                                }
                                """.formatted(code)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.roles[0]").value("ORG_ADMIN"));
    }
}
