package com.codehouse.ciciassistant.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.codehouse.ciciassistant.agent.domain.AgentDefinitionRepository;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceEntity;
import com.codehouse.ciciassistant.ai.domain.AgentRunTraceRepository;
import com.codehouse.ciciassistant.ai.domain.ChatMessageRepository;
import com.codehouse.ciciassistant.ai.domain.ChatSessionRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AgentRunTraceServiceTest {

    @Test
    void retainsRedactedAdminDetailSeparatelyFromCompactTraceSummary() throws Exception {
        AgentRunTraceRepository traces = mock(AgentRunTraceRepository.class);
        AgentRunTraceService service = new AgentRunTraceService(
                traces,
                mock(ChatSessionRepository.class),
                mock(ChatMessageRepository.class),
                mock(AgentDefinitionRepository.class));
        String question = "客户互动工作台上下文：" + "业务事实".repeat(400)
                + " password=super-secret 13900009999";

        service.recordChatRun(new AgentRunTraceService.ChatRunTraceInput(
                "org-1", "user-1", "web:session-1", "agent-1", question, "已完成",
                "model-1", "", "", List.of(), List.of(), null, List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), null, 0, Instant.parse("2026-07-21T00:00:00Z"),
                Instant.parse("2026-07-21T00:00:01Z")));

        ArgumentCaptor<AgentRunTraceEntity> saved = ArgumentCaptor.forClass(AgentRunTraceEntity.class);
        verify(traces).save(saved.capture());
        JsonNode detail = new ObjectMapper().readTree(saved.getValue().getDetailJson());

        assertThat(detail.path("request").path("question").asText()).endsWith("…");
        assertThat(detail.path("request").path("questionDetail").path("text").asText())
                .contains("业务事实")
                .doesNotContain("super-secret")
                .doesNotContain("13900009999");
        assertThat(detail.path("request").path("questionDetail").path("truncated").asBoolean()).isFalse();
    }

    @Test
    void recordsRequestedAndEffectiveForcedSkillContext() throws Exception {
        AgentRunTraceRepository traces = mock(AgentRunTraceRepository.class);
        AgentRunTraceService service = new AgentRunTraceService(
                traces,
                mock(ChatSessionRepository.class),
                mock(ChatMessageRepository.class),
                mock(AgentDefinitionRepository.class));

        service.recordChatRun(new AgentRunTraceService.ChatRunTraceInput(
                "org-1", "user-1", "web:session-1", "agent-1", "整理客户信息", "已完成",
                "model-1", "customer-research", "customer-research", List.of(), List.of(), null,
                List.of(), List.of("customer-research", "other-skill"), List.of("customer-research"),
                List.of(), List.of(), List.of(), List.of(), null, 0,
                Instant.parse("2026-07-21T00:00:00Z"), Instant.parse("2026-07-21T00:00:01Z")));

        ArgumentCaptor<AgentRunTraceEntity> saved = ArgumentCaptor.forClass(AgentRunTraceEntity.class);
        verify(traces).save(saved.capture());
        JsonNode skills = new ObjectMapper().readTree(saved.getValue().getDetailJson()).path("skills");

        assertThat(skills.path("requestedSkillCode").asText()).isEqualTo("customer-research");
        assertThat(skills.path("effectiveSkillCode").asText()).isEqualTo("customer-research");
        assertThat(skills.path("selectionStatus").asText()).isEqualTo("FORCED");
        assertThat(skills.path("selectionReason").asText()).contains("强制业务上下文");
        assertThat(skills.path("activatedSkillCodes").toString()).contains("customer-research");
    }
}
