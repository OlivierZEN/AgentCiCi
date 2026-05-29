package com.codehouse.ciciassistant.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
class AdminBillingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void organizationAdminCanReadOwnBillingChain() throws Exception {
        String token = registerAdminToken();

        MvcResult overviewResult = mockMvc.perform(get("/admin/billing/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.orgId").isNotEmpty())
                .andExpect(jsonPath("$.data.subscription.editionName").isNotEmpty())
                .andExpect(jsonPath("$.data.creditSummary.includedCredits").isNumber())
                .andExpect(jsonPath("$.data.creditSummary.consumedCredits").isNumber())
                .andExpect(jsonPath("$.data.usageByDomain[0].credits").isNumber())
                .andExpect(jsonPath("$.data.recentLedger[0].entryType").value("usage_debit"))
                .andExpect(jsonPath("$.data.recentUsageEvents[0].domain").isNotEmpty())
                .andReturn();

        JsonNode overview = readJson(overviewResult).path("data");
        String orgId = overview.path("subscription").path("orgId").asText();
        assertThat(overview.path("subscription").path("localModelTokenPolicy").asText()).isNotBlank();

        mockMvc.perform(get("/admin/billing/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orgId").value(orgId));

        mockMvc.perform(get("/admin/billing/usage-events")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].credits").isNumber());

        mockMvc.perform(get("/admin/billing/ledger")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].balanceAfter").isNumber());

        mockMvc.perform(get("/admin/billing/quota")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].code").value("credits"));
    }

    private String registerAdminToken() throws Exception {
        String mobile = "139" + String.format("%08d", Math.floorMod(System.nanoTime(), 100_000_000L));
        MvcResult registerResult = mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "mobile": "%s",
                                  "password": "szyd1234",
                                  "organizationName": "TASK-143 计费验证组织"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();

        return readJson(registerResult).path("data").path("token").asText();
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
