package com.codehouse.ciciassistant.datainsight;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "spring.profiles.active=default")
class DataInsightIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldRenderIndependentDataInsightDashboard() throws Exception {
        String token = loginToken("13900009999");

        mockMvc.perform(get("/ai/data-insights/dashboard")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.sourceMode").value("MOCK"))
                .andExpect(jsonPath("$.data.context.dashboardName").value("销售云主页"))
                .andExpect(jsonPath("$.data.summary.totalLeads").isNumber())
                .andExpect(jsonPath("$.data.summary.contractAmount").isNumber())
                .andExpect(jsonPath("$.data.funnel[0].label").value("潜在客户"))
                .andExpect(jsonPath("$.data.rankings.customerCount[0].label").isString())
                .andExpect(jsonPath("$.data.accounts[0].accountName").isString());
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "companyId": "demo-org",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andReturn();
        if (loginResult.getResponse().getStatus() != 200) {
            throw new IllegalStateException("login failed: " + loginResult.getResponse().getContentAsString());
        }
        JsonNode data = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data");
        return data.path("token").asText();
    }
}
