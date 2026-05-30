package com.codehouse.ciciassistant.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class PlatformBillingConfigurationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldExposeAndUpdateConfigurablePrivateAndSaasBillingEditions() throws Exception {
        String platformToken = platformToken();

        MvcResult privateCatalogResult = mockMvc.perform(get("/platform/billing/catalog?deploymentMode=private_deployment")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.editions[0].deploymentMode").value("private_deployment"))
                .andReturn();
        JsonNode privateCatalog = readJson(privateCatalogResult).path("data");
        JsonNode privateDepartment = findByField(privateCatalog.path("editions"), "editionCode", "private_department");
        assertThat(privateDepartment).isNotNull();
        assertThat(privateDepartment.path("billingTypePolicy").asText()).isEqualTo("customer_paid");
        assertThat(privateDepartment.path("localModelTokenPolicy").asText()).contains("不二次收费");
        assertThat(privateCatalog.path("packages")).extracting(node -> node.path("packageType").asText())
                .contains("capacity", "module", "service");

        mockMvc.perform(put("/platform/billing/editions/{editionCode}", "private_department")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "部门版",
                                  "description": "面向单部门私有化试点的年费授权。",
                                  "enabled": true,
                                  "operationSeatLimit": 60,
                                  "builderSeatLimit": 6,
                                  "agentLimit": 24,
                                  "skillLimit": 80,
                                  "workflowLimit": 24,
                                  "knowledgeBaseLimit": 12,
                                  "documentLimit": 6000,
                                  "chunkLimit": 300000,
                                  "knowledgeStorageMb": 153600,
                                  "openApiQps": 60,
                                  "openApiConcurrency": 24,
                                  "openApiCredentialLimit": 12,
                                  "connectorLimit": 6,
                                  "meetingMinutesConcurrency": 2,
                                  "traceRetentionDays": 120,
                                  "auditRetentionDays": 365,
                                  "environmentLimit": 1,
                                  "includedCredits": 0,
                                  "overageMode": "soft_limit",
                                  "billingTypePolicy": "customer_paid",
                                  "slaTierCode": "standard",
                                  "topUpPolicy": "disabled",
                                  "localModelTokenPolicy": "客户自有本地模型 token 只做治理和归因，不二次收费。",
                                  "platformPaidResourcePolicy": "平台代付资源需单独启用 platform_paid。",
                                  "packageCodes": ["private_capacity_pack", "private_service_pack"],
                                  "reason": "TASK-143 验证私有化部门版容量指标可配置"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.operationSeatLimit").value(60))
                .andExpect(jsonPath("$.data.billingTypePolicy").value("customer_paid"))
                .andExpect(jsonPath("$.data.versionNo").value(privateDepartment.path("versionNo").asInt() + 1));

        MvcResult saasCatalogResult = mockMvc.perform(get("/platform/billing/catalog?deploymentMode=saas")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode saasCatalog = readJson(saasCatalogResult).path("data");
        JsonNode saasBusiness = findByField(saasCatalog.path("editions"), "editionCode", "saas_business");
        assertThat(saasBusiness).isNotNull();
        assertThat(saasBusiness.path("billingTypePolicy").asText()).isEqualTo("platform_paid");
        assertThat(saasBusiness.path("includedCredits").decimalValue()).isPositive();

        mockMvc.perform(put("/platform/billing/packages/{packageCode}", "private_capacity_pack")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "私有化容量包",
                                  "description": "扩展私有化容量，不改变版本名称。",
                                  "enabled": true,
                                  "packageType": "capacity",
                                  "configJson": "{\\"agents\\":30,\\"knowledgeStorageMb\\":204800}",
                                  "reason": "TASK-143 验证容量包独立配置"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.packageType").value("capacity"))
                .andExpect(jsonPath("$.data.configJson").value("{\"agents\":30,\"knowledgeStorageMb\":204800}"));

        MvcResult auditResult = mockMvc.perform(get("/platform/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode auditRows = readJson(auditResult).path("data");
        assertThat(auditRows).extracting(node -> node.path("eventType").asText())
                .contains("platform.billing.edition.update", "platform.billing.package.update");
    }

    @Test
    void shouldRequireReasonForMutableBillingConfiguration() throws Exception {
        String platformToken = platformToken();

        mockMvc.perform(put("/platform/billing/editions/{editionCode}", "saas_team")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "displayName": "团队版",
                                  "enabled": true,
                                  "includedCredits": 5000,
                                  "overageMode": "soft_limit",
                                  "billingTypePolicy": "included",
                                  "slaTierCode": "standard",
                                  "topUpPolicy": "manual_top_up",
                                  "localModelTokenPolicy": "SaaS 平台代付模型进入 credits。",
                                  "reason": ""
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("reason must not be blank"));
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

    private String platformToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/platform/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "admin@cloudcc.com",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();

        return readJson(loginResult).path("data").path("token").asText();
    }

    private JsonNode readJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }
}
