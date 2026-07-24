package com.codehouse.ciciassistant.customer.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationEntity;
import com.codehouse.ciciassistant.customer.domain.CustomerWorkbenchRecommendationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class CustomerInteractionActionServiceTest {

    @Test
    void createsAndRefreshesEvidenceDrivenActionWithoutStackingOpenDuplicates() {
        Fixture fixture = new Fixture();
        String first = analysis("CREATE_TASK", "followup:budget-approver", "确认预算审批人", 0.88, "客户要求周一前确认预算审批人", "");

        var created = fixture.service.recordActions("org-1", "account-1", "event-1", "batch-1", Instant.now(), first);

        assertThat(created).containsEntry("generated", 1).containsEntry("refreshed", 0);
        assertThat(fixture.stored).singleElement().satisfies(item -> {
            assertThat(item.getActionKey()).isEqualTo("followup:budget-approver");
            assertThat(item.getSourceEventId()).isEqualTo("event-1");
            assertThat(item.getTriggerType()).isEqualTo("INTERACTION_AI");
            assertThat(item.getStatus()).isEqualTo(CustomerWorkbenchRecommendationEntity.STATUS_PENDING);
            assertThat(item.getEvidenceJson()).contains("客户要求周一前确认预算审批人");
        });

        String updated = analysis("CREATE_TASK", "followup:budget-approver", "跟进预算审批人", 0.92, "客户再次要求今天确认审批人", "");
        var refreshed = fixture.service.recordActions("org-1", "account-1", "event-2", "batch-2", Instant.now(), updated);

        assertThat(refreshed).containsEntry("generated", 0).containsEntry("refreshed", 1);
        assertThat(fixture.stored).singleElement().satisfies(item -> {
            assertThat(item.getTitle()).isEqualTo("跟进预算审批人");
            assertThat(item.getSourceEventId()).isEqualTo("event-2");
            assertThat(item.getEvidenceJson()).contains("客户再次要求今天确认审批人");
        });
    }

    @Test
    void rejectsWeakEvidenceAndOpportunityUpdateWithoutVisibleTarget() {
        Fixture fixture = new Fixture();

        var weak = fixture.service.recordActions("org-1", "account-1", "event-1", "batch-1", Instant.now(),
                analysis("CREATE_OPPORTUNITY", "expansion:mobile", "移动巡检增购", 0.54, "客户也许会考虑移动端", ""));
        var missingTarget = fixture.service.recordActions("org-1", "account-1", "event-2", "batch-2", Instant.now(),
                analysis("UPDATE_OPPORTUNITY", "opportunity:review", "更新方案评审商机", 0.91, "客户确认下周评审", ""));

        assertThat(weak).containsEntry("skipped", 1);
        assertThat(missingTarget).containsEntry("skipped", 1);
        assertThat(fixture.stored).isEmpty();
    }

    @Test
    void allowsACompletedBusinessActionToRecurAfterCooldown() {
        Fixture fixture = new Fixture();
        Instant now = Instant.now();
        String analysis = analysis("CREATE_TASK", "renewal:annual-review", "安排续约评审", 0.9, "客户要求安排年度续约评审", "");
        fixture.service.recordActions("org-1", "account-1", "event-1", "batch-1", now, analysis);
        CustomerWorkbenchRecommendationEntity completed = fixture.stored.get(0);
        completed.accept();
        completed.confirm("user-1");
        completed.markApplying();
        completed.apply("task-1");

        var cooling = fixture.service.recordActions("org-1", "account-1", "event-2", "batch-2", now.plus(2, ChronoUnit.DAYS), analysis);
        var recurring = fixture.service.recordActions("org-1", "account-1", "event-3", "batch-3", now.plus(8, ChronoUnit.DAYS), analysis);

        assertThat(cooling).containsEntry("generated", 0).containsEntry("skipped", 1);
        assertThat(recurring).containsEntry("generated", 1);
        assertThat(fixture.stored).hasSize(2);
    }

    @Test
    void doesNotCreateCurrentActionFromExpiredHistoricalInteraction() {
        Fixture fixture = new Fixture();
        Instant historical = Instant.now().minus(60, ChronoUnit.DAYS);

        var result = fixture.service.recordActions("org-1", "account-1", "event-old", "batch-old", historical,
                analysis("CREATE_TASK", "followup:historical", "历史事项跟进", 0.9, "客户当时要求一周内回复", ""));

        assertThat(result).containsEntry("generated", 0).containsEntry("skipped", 1);
        assertThat(fixture.stored).isEmpty();
    }

    private static String analysis(String type, String key, String title, double confidence, String evidence, String targetRecordId) {
        return """
                {"actionCandidates":[{
                  "actionType":"%s","businessKey":"%s","title":"%s",
                  "reason":"本次沟通出现了明确可执行事项","evidence":"%s",
                  "confidence":%s,"dueInDays":3,"validDays":30,"targetRecordId":"%s"
                }]}
                """.formatted(type, key, title, evidence, confidence, targetRecordId);
    }

    private static final class Fixture {
        private final CustomerWorkbenchRecommendationRepository repository = mock(CustomerWorkbenchRecommendationRepository.class);
        private final List<CustomerWorkbenchRecommendationEntity> stored = new ArrayList<>();
        private final CustomerInteractionActionService service;

        private Fixture() {
            when(repository.findByCompanyIdAndCrmAccountIdAndRecommendationTypeAndActionKeyOrderByUpdatedAtDesc(
                    any(), any(), any(), any()))
                    .thenAnswer(invocation -> stored.stream()
                            .filter(item -> invocation.getArgument(2).equals(item.getRecommendationType()))
                            .filter(item -> invocation.getArgument(3).equals(item.getActionKey()))
                            .sorted((left, right) -> right.getUpdatedAt().compareTo(left.getUpdatedAt()))
                            .toList());
            when(repository.save(any(CustomerWorkbenchRecommendationEntity.class))).thenAnswer(invocation -> {
                CustomerWorkbenchRecommendationEntity value = invocation.getArgument(0);
                stored.removeIf(item -> item.getPublicId().equals(value.getPublicId()));
                stored.add(value);
                return value;
            });
            service = new CustomerInteractionActionService(repository, new ObjectMapper());
        }
    }
}
