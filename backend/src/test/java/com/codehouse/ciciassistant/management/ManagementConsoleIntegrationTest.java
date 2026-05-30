package com.codehouse.ciciassistant.management;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
class ManagementConsoleIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldManageKbModelAndToolLifecycle() throws Exception {
        String token = loginToken("13800138111");
        String toolName = "calendar.read." + UUID.randomUUID().toString().substring(0, 8);

        MvcResult createdKb = mockMvc.perform(post("/kb")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Policy KB",
                                  "description": "original desc"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Policy KB"))
                .andReturn();
        Long kbId = objectMapper.readTree(createdKb.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(put("/kb/{id}", kbId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "name": "Policy KB v2",
                                  "description": "updated desc"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Policy KB v2"));

        MockMultipartFile file = new MockMultipartFile(
                "file", "policy.txt", "text/plain", "sample policy content".getBytes());
        MvcResult uploaded = mockMvc.perform(multipart("/kb/documents/upload")
                        .file(file)
                        .param("knowledgeBaseId", String.valueOf(kbId))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn();
        Long documentId = objectMapper.readTree(uploaded.getResponse().getContentAsString()).path("data").path("id").asLong();

        mockMvc.perform(delete("/kb/documents/{id}", documentId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));

        mockMvc.perform(post("/models")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sceneCode": "qa",
                                  "provider": "aliyun-bailian",
                                  "modelName": "qwen3.5-plus"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("模型厂商和模型路由由运营平台统一配置，组织后台只开放计费用量查看。"));

        mockMvc.perform(delete("/models")
                        .queryParam("sceneCode", "qa")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value("模型厂商和模型路由由运营平台统一配置，组织后台只开放计费用量查看。"));

        mockMvc.perform(post("/tools")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "toolName": "%s",
                                  "description": "read schedules",
                                  "riskLevel": "LOW"
                                }
                                """.formatted(toolName)))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/tools")
                        .queryParam("toolName", toolName)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        mockMvc.perform(delete("/kb/{id}", kbId)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DELETED"));

        mockMvc.perform(get("/kb")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    private String loginToken(String mobile) throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/auth/password/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "orgId": "demo-org",
                                  "mobile": "%s",
                                  "password": "szyd1234"
                                }
                                """.formatted(mobile)))
                .andExpect(status().isOk())
                .andReturn();
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
