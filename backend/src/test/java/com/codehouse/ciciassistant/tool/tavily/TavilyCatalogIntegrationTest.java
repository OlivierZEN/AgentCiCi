package com.codehouse.ciciassistant.tool.tavily;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

/**
 * End-to-end wiring tests for Tavily builtin tools and the {@code web-search} skill.
 *
 * <p>Intentionally uses a mobile not shared with other integration tests so the in-memory
 * SMS rate limiter (store=memory in the test profile) does not reject us across contexts.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.profiles.active=default")
@AutoConfigureMockMvc
class TavilyCatalogIntegrationTest {

    private static final String ADMIN_MOBILE = "13800138188";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldExposeTavilyToolsInUnifiedCatalogAndBindWebSearchToCiciSystem() throws Exception {
        String token = loginToken(ADMIN_MOBILE);

        // /tools catalog must contain both Tavily tools as builtins.
        MvcResult toolsResult = mockMvc.perform(get("/tools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode tools = objectMapper.readTree(toolsResult.getResponse().getContentAsString()).path("data");
        assertThat(toolNames(tools)).contains("tavily_search", "tavily_extract");
        assertThat(findToolRow(tools, "tavily_search").path("builtin").asBoolean()).isTrue();
        assertThat(findToolRow(tools, "tavily_extract").path("builtin").asBoolean()).isTrue();
        assertThat(findToolRow(tools, "tavily_search").path("category").asText()).isEqualTo("web");

        // /skills catalog must contain web-search with Tavily tool whitelist.
        MvcResult skillsResult = mockMvc.perform(get("/skills")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode skills = objectMapper.readTree(skillsResult.getResponse().getContentAsString()).path("data");
        JsonNode webSearch = findByField(skills, "skillCode", "web-search");
        assertThat(webSearch.isMissingNode()).isFalse();
        assertThat(webSearch.path("builtin").asBoolean()).isTrue();
        JsonNode whitelist = webSearch.path("toolWhitelist");
        assertThat(whitelist.isArray()).isTrue();
        assertThat(whitelist).extracting(JsonNode::asText)
                .contains("tavily_search", "tavily_extract");

        // cici-system must have web-search auto-bound with activationMode=intent-route.
        MvcResult bindingsResult = mockMvc.perform(get("/skills/agents/cici-system/bindings")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode bindings = objectMapper.readTree(bindingsResult.getResponse().getContentAsString()).path("data");
        JsonNode webSearchBinding = findByField(bindings, "skillCode", "web-search");
        assertThat(webSearchBinding.isMissingNode())
                .as("web-search skill must be bound to cici-system by default")
                .isFalse();
        assertThat(webSearchBinding.path("activationMode").asText()).isEqualTo("intent-route");
        assertThat(webSearchBinding.path("enabled").asBoolean()).isTrue();
    }

    private java.util.List<String> toolNames(JsonNode tools) {
        java.util.List<String> out = new java.util.ArrayList<>();
        tools.forEach(row -> out.add(row.path("toolName").asText()));
        return out;
    }

    private JsonNode findToolRow(JsonNode tools, String toolName) {
        for (JsonNode row : tools) {
            if (toolName.equals(row.path("toolName").asText())) {
                return row;
            }
        }
        return objectMapper.missingNode();
    }

    private JsonNode findByField(JsonNode arr, String field, String value) {
        for (JsonNode row : arr) {
            if (value.equals(row.path(field).asText())) {
                return row;
            }
        }
        return objectMapper.missingNode();
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult sendResult = mockMvc.perform(post("/auth/sms/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        String code = objectMapper.readTree(sendResult.getResponse().getContentAsString())
                .path("data").path("devCode").asText();

        MvcResult loginResult = mockMvc.perform(post("/auth/sms/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "code": "%s"
                                }
                                """.formatted(mobile, code)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString())
                .path("data").path("token").asText();
    }
}
