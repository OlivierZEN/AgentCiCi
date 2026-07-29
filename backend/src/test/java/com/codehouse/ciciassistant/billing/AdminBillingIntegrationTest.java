package com.codehouse.ciciassistant.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.ai.service.AgentRunTraceService;
import com.codehouse.ciciassistant.billing.service.BillingUsageMeteringService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(properties = "app.billing.deployment-mode=saas")
@AutoConfigureMockMvc
class AdminBillingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private BillingUsageMeteringService billingUsageMeteringService;

    @Test
    void companyAdminCanReadBillingOverviewWithCompanyMemberSeatCountAndRealtimeUsageDebitsCredits() throws Exception {
        String token = registerAdminToken();

        MvcResult overviewResult = mockMvc.perform(get("/admin/billing/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.subscription.companyId").isNotEmpty())
                .andExpect(jsonPath("$.data.subscription.editionName").isNotEmpty())
                .andExpect(jsonPath("$.data.subscription.editionCode").value("saas_business"))
                .andExpect(jsonPath("$.data.subscription.editionName").value("专业版"))
                .andExpect(jsonPath("$.data.subscription.includedCredits").value(35000))
                .andExpect(jsonPath("$.data.subscription.operationSeatsUsed").value(1))
                .andExpect(jsonPath("$.data.subscription.builderSeatsUsed").value(1))
                .andExpect(jsonPath("$.data.creditSummary.includedCredits").isNumber())
                .andExpect(jsonPath("$.data.creditSummary.consumedCredits").isNumber())
                .andExpect(jsonPath("$.data.quotaWarnings[1].message").value("1 / 100"))
                .andExpect(jsonPath("$.data.quotaWarnings[2].message").value("1 / 2"))
                .andReturn();

        JsonNode overview = readJson(overviewResult).path("data");
        String companyId = overview.path("subscription").path("companyId").asText();
        assertThat(overview.path("subscription").path("localModelTokenPolicy").asText()).isNotBlank();
        assertThat(overview.path("creditSummary").path("remainingCredits").decimalValue())
                .isEqualByComparingTo(overview.path("creditSummary").path("includedCredits").decimalValue());

        billingUsageMeteringService.recordChatRun(new BillingUsageMeteringService.ChatRunMeteringInput(
                companyId,
                "integration-user",
                "integration-agent",
                "billing-realtime-session-1",
                "qwen-plus",
                List.of(new AgentRunTraceService.ModelCallTraceInput(
                        "final_completion",
                        "qwen-plus",
                        "SUCCESS",
                        Instant.now().minusSeconds(2),
                        Instant.now().minusSeconds(1),
                        1000,
                        0,
                        120,
                        2000,
                        1000,
                        "integration model usage")),
                List.of(),
                null,
                120,
                true,
                Instant.now()));
        billingUsageMeteringService.recordChatRun(new BillingUsageMeteringService.ChatRunMeteringInput(
                companyId,
                "integration-user",
                "integration-agent",
                "billing-realtime-session-1",
                "qwen-plus",
                List.of(new AgentRunTraceService.ModelCallTraceInput(
                        "final_completion",
                        "qwen-plus",
                        "SUCCESS",
                        Instant.now().minusSeconds(2),
                        Instant.now().minusSeconds(1),
                        1000,
                        0,
                        120,
                        2000,
                        1000,
                        "integration model usage")),
                List.of(),
                null,
                120,
                true,
                Instant.now()));

        MvcResult debitedOverviewResult = mockMvc.perform(get("/admin/billing/overview")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.creditSummary.consumedCredits").value(1.9))
                .andExpect(jsonPath("$.data.creditSummary.remainingCredits").value(34998.1))
                .andExpect(jsonPath("$.data.usageByDomain[0].credits").isNumber())
                .andExpect(jsonPath("$.data.recentLedger[0].entryType").value("usage_debit"))
                .andReturn();
        JsonNode debitedOverview = readJson(debitedOverviewResult).path("data");
        assertThat(debitedOverview.path("recentUsageEvents")).hasSize(3);
        assertThat(debitedOverview.path("recentUsageEvents"))
                .extracting(node -> node.path("itemCode").asText())
                .containsExactlyInAnyOrder("conversation_credit", "model_token_credit", "workflow_credit");
        assertThat(debitedOverview.path("recentUsageEvents"))
                .allMatch(node -> "Credits 包".equals(node.path("officialPricingItem").asText()));
        assertThat(debitedOverview.path("recentUsageEvents"))
                .allMatch(node -> node.path("explanation").asText().contains("Credits"));
        assertThat(debitedOverview.path("recentUsageEvents"))
                .allMatch(node -> !node.path("quantityLabel").asText().isBlank());
        assertThat(debitedOverview.path("recentLedger").get(0).path("balanceAfter").decimalValue())
                .isEqualByComparingTo("34998.10");

        mockMvc.perform(get("/admin/billing/subscription")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.companyId").value(companyId));

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
                                  "companyName": "TASK-143 计费验证组织"
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
