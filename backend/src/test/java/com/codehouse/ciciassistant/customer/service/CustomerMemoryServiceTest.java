package com.codehouse.ciciassistant.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerMemoryItemEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerMemoryItemRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CustomerMemoryServiceTest {

    private CustomerMemoryItemRepository repository;
    private CustomerMemoryService service;

    @BeforeEach
    void setUp() {
        repository = mock(CustomerMemoryItemRepository.class);
        CustomerInteractionEventRepository eventRepository = mock(CustomerInteractionEventRepository.class);
        when(eventRepository.findByCompanyIdAndCrmAccountIdOrderByOccurredAtDesc("org-1", "account-1"))
                .thenReturn(List.of());
        when(repository.saveAll(anyList())).thenAnswer(invocation -> invocation.getArgument(0));
        service = new CustomerMemoryService(repository, eventRepository, new ObjectMapper());
    }

    @Test
    void keepsDefaultContextInsideNinetyDaysAndExpandsForHistoryQuestions() {
        when(repository.findByCompanyIdAndCrmAccountIdAndStatusOrderByOccurredAtDesc(
                "org-1", "account-1", CustomerMemoryItemEntity.STATUS_ACTIVE)).thenReturn(List.of());
        Map<String, Object> customer = customer(List.of(
                timeline("event-new", Instant.now().minus(2, ChronoUnit.DAYS)),
                timeline("event-old", Instant.now().minus(400, ChronoUnit.DAYS))));

        var current = service.buildAssistantContext("org-1", "account-1", "下一步做什么", customer);
        var history = service.buildAssistantContext("org-1", "account-1", "查询以前的历史沟通", customer);

        assertThat(current.recentInteractions()).extracting(item -> item.get("eventId"))
                .containsExactly("event-new");
        assertThat(current.metadata().get("recentWindowDays")).isEqualTo(90);
        assertThat(history.recentInteractions()).extracting(item -> item.get("eventId"))
                .containsExactly("event-new", "event-old");
        assertThat(history.metadata().get("historyRequested")).isEqualTo(true);
    }

    @Test
    void retainsUnresolvedMemoryAndPrioritizesExplicitArchiveReference() {
        CustomerMemoryItemEntity risk = memory("event-risk", "RISK", "客户预算仍未确认", 5);
        CustomerMemoryItemEntity referenced = memory("event-target", "FACT", "客户确认了部署范围", 40);
        when(repository.findByCompanyIdAndCrmAccountIdAndStatusOrderByOccurredAtDesc(
                "org-1", "account-1", CustomerMemoryItemEntity.STATUS_ACTIVE))
                .thenReturn(List.of(risk, referenced));

        var context = service.buildAssistantContext(
                "org-1", "account-1", "基于 event-target 继续分析", customer(List.of()));

        assertThat(context.activeMemories()).extracting(item -> item.get("sourceEventId"))
                .containsExactly("event-target", "event-risk");
        assertThat(context.evidence().getFirst().get("eventId")).isEqualTo("event-target");
        assertThat(context.evidence()).hasSize(2);
    }

    @Test
    void convertsStructuredAnalysisIntoTypedCustomerMemory() {
        String analysis = """
                {"customerNeeds":["补充权限清单"],"risks":["预算未确认"],
                 "nextActions":["周二前发送方案"],"summary":"方案评审准备中"}
                """;

        List<Map<String, Object>> result = service.replaceForEvent(
                "org-1", "account-1", "event-1", "batch-1", Instant.now(), analysis, List.of("asset-1"));

        assertThat(result).extracting(item -> item.get("type"))
                .containsExactlyInAnyOrder("NEED", "RISK", "NEXT_ACTION");
        assertThat(result).allSatisfy(item -> {
            assertThat(item.get("sourceEventId")).isEqualTo("event-1");
            assertThat(item.get("evidence")).isEqualTo(List.of("asset-1"));
        });
    }

    private CustomerMemoryItemEntity memory(String eventId, String type, String content, long daysAgo) {
        return new CustomerMemoryItemEntity(
                "memory-" + eventId, "org-1", "account-1", eventId, "batch-" + eventId,
                type, content, 0.9, Instant.now().minus(daysAgo, ChronoUnit.DAYS), "[]");
    }

    private Map<String, Object> customer(List<Map<String, Object>> timeline) {
        return Map.of("accountId", "account-1", "name", "测试客户", "timeline", timeline);
    }

    private Map<String, Object> timeline(String eventId, Instant occurredAt) {
        return Map.of(
                "eventId", eventId,
                "occurredAt", occurredAt.toString(),
                "subject", "客户互动",
                "summary", "互动摘要",
                "archiveAvailable", true);
    }
}
