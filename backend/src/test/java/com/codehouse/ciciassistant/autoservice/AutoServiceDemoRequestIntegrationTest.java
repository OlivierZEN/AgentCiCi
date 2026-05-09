package com.codehouse.ciciassistant.autoservice;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class AutoServiceDemoRequestIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSubmitDemoRequestAndListItInPlatformConsole() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
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
        String token = objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();

        MvcResult submitResult = mockMvc.perform(post("/api/autoservice/demo-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "site": "china",
                                  "locale": "zh-CN",
                                  "companyName": "示例售后科技",
                                  "contactName": "王女士",
                                  "mobile": "13800138001",
                                  "email": "demo@example.com",
                                  "roleTitle": "售后负责人",
                                  "scenario": "企微售后与工单流转",
                                  "sourcePath": "/autoservice/cn"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NEW"))
                .andReturn();
        long requestId = objectMapper.readTree(submitResult.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(get("/platform/autoservice/demo-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .param("q", "示例售后"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(requestId))
                .andExpect(jsonPath("$.data.items[0].companyName").value("示例售后科技"))
                .andExpect(jsonPath("$.data.items[0].status").value("NEW"));

        mockMvc.perform(get("/platform/autoservice/demo-requests")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items[0].id").value(requestId));

        mockMvc.perform(patch("/platform/autoservice/demo-requests/{id}", requestId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status": "CONTACTED",
                                  "handledNote": "已电话联系"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONTACTED"));
    }

    @Test
    void shouldRejectInvalidDemoRequestContactPhone() throws Exception {
        mockMvc.perform(post("/api/autoservice/demo-requests")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "site": "china",
                                  "locale": "zh-CN",
                                  "companyName": "示例售后科技",
                                  "contactName": "王女士",
                                  "mobile": "bad",
                                  "sourcePath": "/autoservice/cn"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }
}
