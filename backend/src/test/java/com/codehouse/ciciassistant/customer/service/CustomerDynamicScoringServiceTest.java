package com.codehouse.ciciassistant.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.customer.domain.CustomerDynamicSignalEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerDynamicSignalRepository;
import com.codehouse.ciciassistant.customer.domain.CustomerScoreSnapshotEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerScoreSnapshotRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class CustomerDynamicScoringServiceTest {

    @Test
    void aggregatesAuditableSignalsAndKeepsLowConfidenceEvidencePending() {
        CustomerDynamicSignalRepository signalRepository = mock(CustomerDynamicSignalRepository.class);
        CustomerScoreSnapshotRepository snapshotRepository = mock(CustomerScoreSnapshotRepository.class);
        List<CustomerDynamicSignalEntity> stored = new ArrayList<>();
        when(signalRepository.findByOrgIdAndSourceEventId("org-1", "event-1")).thenAnswer(invocation -> new ArrayList<>(stored));
        when(signalRepository.findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc("org-1", "account-1"))
                .thenAnswer(invocation -> new ArrayList<>(stored));
        when(signalRepository.saveAll(anyList())).thenAnswer(invocation -> {
            List<CustomerDynamicSignalEntity> values = invocation.getArgument(0);
            for (CustomerDynamicSignalEntity value : values) {
                stored.removeIf(item -> item.getPublicId().equals(value.getPublicId()));
                stored.add(value);
            }
            return values;
        });
        when(snapshotRepository.findByOrgIdAndCrmAccountId("org-1", "account-1")).thenReturn(Optional.empty());
        when(snapshotRepository.save(any(CustomerScoreSnapshotEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CustomerDynamicScoringService service = new CustomerDynamicScoringService(
                signalRepository, snapshotRepository, new ObjectMapper());
        String analysis = """
                {"scoringSignals":[
                  {"dimension":"RELATIONSHIP","direction":"POSITIVE","impact":8,"confidence":0.9,
                   "title":"关键人明确支持","evidence":"运营总监明确表示支持方案","reason":"关键决策关系改善","validDays":120},
                  {"dimension":"RISK","direction":"NEGATIVE","impact":9,"confidence":0.9,
                   "title":"投诉升级风险","evidence":"客户要求本周内解决，否则升级投诉","reason":"明确的升级时限","validDays":30},
                  {"dimension":"EXPANSION","direction":"POSITIVE","impact":7,"confidence":0.54,
                   "title":"可能扩容","evidence":"后续也许会考虑扩容","reason":"意向仍不确定","validDays":90}
                ]}
                """;

        var result = service.recordAnalysis("org-1", "account-1", "event-1", "batch-1", "MEETING",
                Instant.now(), analysis);

        assertThat(result).containsEntry("activeSignalCount", 2).containsEntry("pendingSignalCount", 1);
        assertThat((int) result.get("healthScore")).isLessThan(50);
        assertThat(stored).hasSize(3);
        assertThat(stored).filteredOn(item -> CustomerDynamicSignalEntity.STATUS_PENDING.equals(item.getStatus()))
                .singleElement().extracting(CustomerDynamicSignalEntity::getTitle).isEqualTo("可能扩容");

        service.recordAnalysis("org-1", "account-1", "event-1", "batch-1", "MEETING", Instant.now(), analysis);
        assertThat(stored).hasSize(3);
    }
}
