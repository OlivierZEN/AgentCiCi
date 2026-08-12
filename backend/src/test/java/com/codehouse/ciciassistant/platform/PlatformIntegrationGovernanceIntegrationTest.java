package com.codehouse.ciciassistant.platform;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.codehouse.ciciassistant.integration.domain.IntegrationAppRepository;
import com.codehouse.ciciassistant.integration.service.IntegrationAppService;
import com.codehouse.ciciassistant.tool.tavily.TavilyToolService;
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
class PlatformIntegrationGovernanceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private IntegrationAppRepository integrationAppRepository;

    @Autowired
    private TavilyToolService tavilyToolService;

    @Test
    void platformManagesTavilyAndIflytekWhileOrgAdminCannotSeeOrWriteThem() throws Exception {
        String platformToken = platformToken();
        String orgToken = orgToken();

        mockMvc.perform(get("/platform/integrations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.appCode == 'tavily')]").exists())
                .andExpect(jsonPath("$.data[?(@.appCode == 'iflytek_asr')]").exists())
                .andExpect(jsonPath("$.data[?(@.appCode == 'code_interpreter')]").exists())
                .andExpect(jsonPath("$.data[?(@.appCode == 'cloudcc_crm')]").doesNotExist());

        mockMvc.perform(put("/platform/integrations/{appCode}", "tavily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "description": "平台 Tavily",
                                  "config": {
                                    "apiKey": "tvly-platform-key",
                                    "defaultMaxResults": "3"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appCode").value("tavily"))
                .andExpect(jsonPath("$.data.config.apiKey").value(IntegrationAppService.API_KEY_MASK));

        mockMvc.perform(put("/platform/integrations/{appCode}", "iflytek_asr")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "description": "平台讯飞",
                                  "config": {
                                    "appId": "platform-iflytek-app",
                                    "accessKeyId": "platform-iflytek-key",
                                    "accessKeySecret": "platform-iflytek-secret"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appCode").value("iflytek_asr"))
                .andExpect(jsonPath("$.data.config.accessKeySecret").value(IntegrationAppService.IFLYTEK_SECRET_MASK));

        mockMvc.perform(put("/platform/integrations/{appCode}", "code_interpreter")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + platformToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": true,
                                  "description": "平台代码解释器",
                                  "config": {
                                    "apiKey": "sk-ws-platform-key",
                                    "apiBaseUrl": "https://workspace-id.cn-beijing.maas.aliyuncs.com/compatible-mode/v1",
                                    "model": "qwen3.5-plus",
                                    "timeoutMs": "120000",
                                    "maxInputChars": "12000"
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.appCode").value("code_interpreter"))
                .andExpect(jsonPath("$.data.config.apiKey").value(IntegrationAppService.CODE_INTERPRETER_SECRET_MASK));

        MvcResult orgList = mockMvc.perform(get("/integrations")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgToken))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode orgApps = objectMapper.readTree(orgList.getResponse().getContentAsString()).path("data");
        assertThat(orgApps).extracting(node -> node.path("appCode").asText())
                .contains("cloudcc_crm", "feishu_bot")
                .doesNotContain("tavily", "iflytek_asr", "code_interpreter");

        mockMvc.perform(put("/integrations/{appCode}", "tavily")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "enabled": false,
                                  "description": "tenant tavily",
                                  "config": {"apiKey": "tvly-tenant-key"}
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(IntegrationAppService.PLATFORM_MANAGED_MESSAGE));

        mockMvc.perform(post("/integrations/tavily/test")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + orgToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(IntegrationAppService.PLATFORM_MANAGED_MESSAGE));

        assertThat(tavilyToolService.resolveApiKey("other-org")).isEqualTo("tvly-platform-key");
        String platformConfigJson = integrationAppRepository.findByCompanyIdAndAppCode("demo-org", "tavily")
                .orElseThrow()
                .getConfigJson();
        assertThat(platformConfigJson).doesNotContain("tvly-platform-key");
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
                                  "companyId": "demo-org",
                                  "mobile": "13800138111",
                                  "password": "szyd1234"
                                }
                                """))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
