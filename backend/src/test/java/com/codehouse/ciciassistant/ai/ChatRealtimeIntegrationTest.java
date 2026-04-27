package com.codehouse.ciciassistant.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
@TestPropertySource(properties = "spring.profiles.active=default")
@AutoConfigureMockMvc
class ChatRealtimeIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldPushSessionUpdatedEventWhenChatSessionChanges() throws Exception {
        String token = loginToken("13800138007");

        MvcResult streamResult = mockMvc.perform(get("/ai/sessions/stream")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(post("/ai/chat")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "sessionId": "s-realtime-1",
                                  "question": "帮我回顾一下今天的线索同步情况",
                                  "knowledgeBaseIds": []
                                }
                                """))
                .andExpect(status().isOk());

        Thread.sleep(300L);

        String streamBody = streamResult.getResponse().getContentAsString();
        assertThat(streamBody).contains("event:connected");
        assertThat(streamBody).contains("event:session_updated");
        assertThat(streamBody).contains("\"sessionId\":\"s-realtime-1\"");

        if (streamResult.getRequest().getAsyncContext() != null) {
            streamResult.getRequest().getAsyncContext().complete();
        }
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
        JsonNode send = objectMapper.readTree(sendResult.getResponse().getContentAsString());
        String code = send.path("data").path("devCode").asText();

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
        return objectMapper.readTree(loginResult.getResponse().getContentAsString()).path("data").path("token").asText();
    }
}
