package com.codehouse.ciciassistant.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.codehouse.ciciassistant.memory.service.*;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TrustedMemoryRuntimeContextServiceTest {
    @Test void injectsOnlyInsideTheExplicitTrustedScopeAndForTheResolvedAgent() {
        ExternalMemoryContextService contexts=mock(ExternalMemoryContextService.class);
        MemorySemanticRetrievalService retrieval=mock(MemorySemanticRetrievalService.class);
        TrustedMemoryRuntimeContextService service=new TrustedMemoryRuntimeContextService(contexts, retrieval);
        ExternalMemoryContextService.ExternalMemoryContext context=new ExternalMemoryContextService.ExternalMemoryContext("org", "app", "conversation", "subject", "EXTERNAL_USER", "VERIFIED");
        when(contexts.loadContext(eq(context), eq("agent-a"), anySet(), isNull())).thenReturn(new ExternalMemoryContextService.MemoryContext(1L, "已确认", List.of()));
        when(retrieval.retrieve(eq(context), eq("agent-a"), anySet(), eq("question"), eq(4))).thenReturn(List.of());

        assertThat(service.buildPrompt("org", "agent-a", "question")).isEmpty();
        try (TrustedMemoryRuntimeContextService.Scope ignored=service.enter(new TrustedMemoryRuntimeContextService.TrustedMemoryRequest(context, "agent-a", Set.of()))) {
            assertThat(service.buildPrompt("org", "agent-b", "question")).isEmpty();
            assertThat(service.buildPrompt("org", "agent-a", "question")).contains("已确认");
            assertThat(service.traceMetadata()).containsEntry("memoryInjected", true)
                    .containsEntry("structuredRecordCount", 0)
                    .containsEntry("semanticHitCount", 0);
        }
        assertThat(service.buildPrompt("org", "agent-a", "question")).isEmpty();
        assertThat(service.traceMetadata()).containsEntry("memoryInjected", false);
    }
}
