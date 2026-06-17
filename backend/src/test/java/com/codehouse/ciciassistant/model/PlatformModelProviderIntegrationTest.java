package com.codehouse.ciciassistant.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
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
class PlatformModelProviderIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void platformCanGovernModelProvidersWhileOrganizationProviderWritesAreForbidden() throws Exception {
        String platformToken = platformToken();
        String orgToken = orgToken();

        mockMvc.perform(get("/platform/models/providers")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.providerCode == 'aliyun-bailian')]").exists())
                .andExpect(jsonPath("$.data[?(@.providerCode == 'onekeytoken' && @.defaultBaseUrl == 'https://my.onekeytoken.com/v1')]").exists());

        mockMvc.perform(post("/platform/models/providers/{providerCode}/models/fetch", "onekeytoken")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.catalogSource").value("static"))
                .andExpect(jsonPath("$.data.remoteFetchSupported").value(false))
                .andExpect(jsonPath("$.data.models[?(@ == 'onekeytoken/auto')]").exists())
                .andExpect(jsonPath("$.data.models[?(@ == 'deepseek-chat')]").exists())
                .andExpect(jsonPath("$.data.models[?(@ == 'qwen3.5-flash')]").exists());

        mockMvc.perform(put("/platform/models/providers/{providerCode}", "deepseek")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "apiBaseUrl": "https://platform.deepseek.example/v1",
                                  "apiKey": "platform-secret"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.providerCode").value("deepseek"))
                .andExpect(jsonPath("$.data.apiBaseUrl").value("https://platform.deepseek.example/v1"))
                .andExpect(jsonPath("$.data.apiKeySet").value(true));

        mockMvc.perform(put("/platform/models/providers/{providerCode}/selected-models", "deepseek")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "selectedModels": ["deepseek-chat", "deepseek-reasoner"]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.selectedModels[0]").value("deepseek-chat"));

        mockMvc.perform(put("/platform/models/routes/{sceneCode}", "chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "providerCode": "deepseek",
                                  "modelName": "deepseek-chat"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("chat"))
                .andExpect(jsonPath("$.data.providerCode").value("deepseek"))
                .andExpect(jsonPath("$.data.modelName").value("deepseek-chat"))
                .andExpect(jsonPath("$.data.available").value(true));

        mockMvc.perform(get("/platform/models/routes")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.routes[?(@.sceneCode == 'chat' && @.modelName == 'deepseek-chat')]").exists())
                .andExpect(jsonPath("$.data.modelCandidates[?(@.modelName == 'deepseek-chat')]").exists());

        mockMvc.perform(delete("/platform/models/routes/{sceneCode}", "chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sceneCode").value("chat"))
                .andExpect(jsonPath("$.data.configured").value(false));

        MvcResult auditResult = mockMvc.perform(get("/platform/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .param("q", "deepseek")
                        .param("limit", "20"))
                .andExpect(status().isOk())
                .andReturn();
        String auditBody = auditResult.getResponse().getContentAsString();
        assertThat(auditBody).doesNotContain("platform-secret");
        JsonNode auditItems = objectMapper.readTree(auditBody).path("data").path("items");
        assertThat(auditItems).extracting(node -> node.path("eventType").asText())
                .contains(
                        "platform.model.provider.update",
                        "platform.model.selected_models.update",
                        "platform.model.route.update"
                );

        MvcResult routeDeleteAuditResult = mockMvc.perform(get("/platform/audit/logs")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .param("eventType", "platform.model.route.delete")
                        .param("resourceType", "model_route")
                        .param("q", "chat")
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode routeDeleteAuditItems = objectMapper.readTree(routeDeleteAuditResult.getResponse().getContentAsString())
                .path("data").path("items");
        assertThat(routeDeleteAuditItems).extracting(node -> node.path("resourceKey").asText()).contains("chat");

        mockMvc.perform(put("/models/providers/{providerCode}", "deepseek")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false,
                                  "apiBaseUrl": "https://tenant.example/v1",
                                  "apiKey": "tenant-secret"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("模型厂商和模型路由由运营平台统一配置，组织后台只开放计费用量查看。"));
    }

    private String platformToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/platform/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "identifier": "admin@cloudcc.com",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }

    private String orgToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "13800138111",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
