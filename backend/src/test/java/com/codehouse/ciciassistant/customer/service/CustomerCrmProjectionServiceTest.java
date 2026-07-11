package com.codehouse.ciciassistant.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.cloudcc.CloudccOpenApiService;
import com.codehouse.ciciassistant.customer.domain.CustomerFollowSubscriptionRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerInteractionEventRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerSignalRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerSignalEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.ArrayList;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;

class CustomerCrmProjectionServiceTest {

    @Test
    void projectsPermissionScopedCrmObjectsIntoSeparateNewAndExistingQueues() {
        CloudccOpenApiService cloudcc = mock(CloudccOpenApiService.class);
        CustomerInteractionEventRepository events = mock(CustomerInteractionEventRepository.class);
        CustomerWorkbenchRecommendationRepository recommendations = mock(CustomerWorkbenchRecommendationRepository.class);
        CustomerSignalRepository signals = mock(CustomerSignalRepository.class);
        CustomerFollowSubscriptionRepository follows = mock(CustomerFollowSubscriptionRepository.class);
        CustomerCrmProjectionService service = new CustomerCrmProjectionService(
                cloudcc, events, recommendations, signals, follows, new ObjectMapper());

        when(cloudcc.queryAllRecords(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenAnswer(invocation -> switch (invocation.getArgument(2, String.class)) {
                    case "Account" -> List.of(
                            Map.of("id", "001-new", "name", "新客户甲", "lastmodifydate", "2026-07-10 10:00:00"),
                            Map.of("id", "001-old", "name", "老客户乙", "lastmodifydate", "2026-07-10 09:00:00"));
                    case "Contact" -> List.of(Map.of("id", "003-a", "name", "李经理", "khmc", "001-new", "zhiwu", "采购经理"));
                    case "Opportunity" -> List.of(
                            Map.of("id", "006-a", "name", "甲项目", "khmc", "001-new", "jieduan", "2-需求分析", "xyb", "安排方案会"),
                            Map.of("id", "006-b", "name", "乙续约", "khmc", "001-old", "jieduan", "7-签约关单"));
                    case "Task" -> List.of(Map.of("id", "00T-a", "subject", "补充需求", "relateid", "001-new",
                            "status", "未开始", "expiredate", "2026-07-12", "lastmodifydate", "2026-07-10 11:00:00"));
                    case "Event" -> List.of();
                    case "cloudcccase" -> List.of(Map.of("id", "500-b", "name", "CASE-1", "khmc", "001-old",
                            "zhuangtai", "新建", "zhuti", "响应问题"));
                    case "contract" -> List.of(Map.of("id", "800-b", "name", "乙合同", "khmc", "001-old",
                            "zhuangtai", "已启用", "htjsrq", "2026-09-01", "htje", "120000"));
                    default -> List.of();
                });
        when(follows.findByOrgIdAndUserId("org", "user")).thenReturn(List.of());
        when(follows.findByOrgIdAndUserIdAndCrmAccountId(anyString(), anyString(), anyString())).thenReturn(Optional.empty());
        when(events.findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc(anyString(), anyString())).thenReturn(List.of());
        when(recommendations.countByOrgIdAndCrmAccountIdAndStatus(anyString(), anyString(), anyString())).thenReturn(0L);
        when(signals.findByOrgIdAndPublicId(anyString(), anyString())).thenReturn(Optional.empty());
        when(signals.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        Map<String, Object> newQueue = service.queue("org", "user",
                new CustomerCrmProjectionService.QueueQuery("new", "all", "priority", "desc", "", 1, 20, false));
        Map<String, Object> existingQueue = service.queue("org", "user",
                new CustomerCrmProjectionService.QueueQuery("existing", "all", "priority", "desc", "", 1, 20, false));

        assertThat(items(newQueue)).extracting(item -> item.get("name")).containsExactly("新客户甲");
        assertThat(items(existingQueue)).extracting(item -> item.get("name")).containsExactly("老客户乙");
        Map<String, Object> detail = service.detail("org", "user", "001-old", false);
        assertThat(detail).containsEntry("customerMode", "EXISTING").containsEntry("source", "CLOUDCC_LIVE");
        assertThat((List<?>) detail.get("serviceIssues")).hasSize(1);
        assertThat((List<?>) detail.get("valueItems")).hasSize(2);
        assertThat((List<?>) detail.get("signals")).isNotEmpty();
    }

    @Test
    void serializesSignalPersistenceForTheSameAccount() throws Exception {
        CustomerSignalRepository signals = mock(CustomerSignalRepository.class);
        ConcurrentHashMap<String, CustomerSignalEntity> stored = new ConcurrentHashMap<>();
        when(signals.findByOrgIdAndPublicId(anyString(), anyString())).thenAnswer(invocation ->
                Optional.ofNullable(stored.get(invocation.getArgument(1, String.class))));
        when(signals.findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(anyString(), anyString())).thenAnswer(invocation ->
                new ArrayList<>(stored.values()));
        when(signals.save(any())).thenAnswer(invocation -> {
            CustomerSignalEntity entity = invocation.getArgument(0);
            CustomerSignalEntity existing = stored.putIfAbsent(entity.getPublicId(), entity);
            if (existing != null && existing != entity) {
                throw new IllegalStateException("duplicate signal insert");
            }
            return entity;
        });
        CustomerCrmProjectionService service = new CustomerCrmProjectionService(
                mock(CloudccOpenApiService.class), mock(CustomerInteractionEventRepository.class),
                mock(CustomerWorkbenchRecommendationRepository.class), signals,
                mock(CustomerFollowSubscriptionRepository.class), new ObjectMapper());
        List<Map<String, Object>> projected = List.of(Map.of(
                "mode", "EXISTING", "type", "SERVICE_RISK", "title", "服务风险",
                "detail", "存在未关闭服务问题", "severity", "HIGH", "evidence", List.of("case-1")));

        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            CountDownLatch start = new CountDownLatch(1);
            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(executor.submit(() -> {
                    start.await();
                    service.persistSignals("org", "account", projected);
                    return null;
                }));
            }
            start.countDown();
            for (Future<?> future : futures) future.get();
            assertThat(stored).hasSize(1);
        } finally {
            executor.shutdownNow();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> items(Map<String, Object> queue) {
        return (List<Map<String, Object>>) queue.get("items");
    }
}
