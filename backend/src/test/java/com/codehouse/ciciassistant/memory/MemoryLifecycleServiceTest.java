package com.codehouse.ciciassistant.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.memory.domain.MemoryCandidateRepository;
import com.codehouse.ciciassistant.memory.domain.MemoryConversationSnapshotRepository;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordRepository;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectEntity;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectRepository;
import com.codehouse.ciciassistant.memory.service.MemoryLifecycleService;
import com.codehouse.ciciassistant.memory.service.MemorySemanticRetrievalService;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.platform.domain.OrganizationRetentionPolicyEntity;
import com.codehouse.ciciassistant.platform.domain.OrganizationRetentionPolicyRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemoryLifecycleServiceTest {
    @Test void deletionImmediatelyRevokesAndRedactsRecordsWhenVectorDeletionFails() throws Exception {
        MemorySubjectRepository subjects=mock(MemorySubjectRepository.class);
        MemoryRecordRepository records=mock(MemoryRecordRepository.class);
        MemoryCandidateRepository candidates=mock(MemoryCandidateRepository.class);
        MemoryConversationSnapshotRepository snapshots=mock(MemoryConversationSnapshotRepository.class);
        MemorySemanticRetrievalService semantic=mock(MemorySemanticRetrievalService.class);
        OrganizationRetentionPolicyRepository policies=mock(OrganizationRetentionPolicyRepository.class);
        AuditService audit=mock(AuditService.class);
        MemorySubjectEntity subject=subject(7L, "subject-a");
        MemoryRecordEntity record=record(8L, Instant.now().plusSeconds(300));
        when(subjects.findByOrgIdAndApplicationCodeAndSubjectTypeAndExternalRef("org-a", "app-a", "EXTERNAL_USER", "subject-a"))
                .thenReturn(Optional.of(subject));
        when(records.findByOrgIdAndSubjectId("org-a", 7L)).thenReturn(List.of(record));
        when(candidates.findByOrgIdAndSubjectId("org-a", 7L)).thenReturn(List.of());
        when(snapshots.deleteByOrgIdAndSubjectId("org-a", 7L)).thenReturn(2L);
        when(semantic.remove(record)).thenReturn(false);
        when(policies.findById("org-a")).thenReturn(Optional.empty());
        MemoryLifecycleService service=service(subjects, records, candidates, snapshots, semantic, policies, audit);

        record.assignAgent("agent-a");
        var result=service.deleteSubject("org-a", "app-a", "EXTERNAL_USER", "subject-a", "agent-a", "actor-a", "right to erase");

        assertThat(result.records()).isEqualTo(1);
        assertThat(result.vectorFailures()).isEqualTo(1);
        assertThat(record.getStatus()).isEqualTo("REVOKED");
        assertThat(record.getContent()).isEqualTo("[deleted]");
        assertThat(subject.getExternalRef()).isEqualTo("deleted-7");
        verify(audit).log(any(), any(), any(), contains("vectorFailures=1"));
    }

    @Test void legalHoldBlocksSubjectDeletionAndExpiryCleanup() throws Exception {
        MemorySubjectRepository subjects=mock(MemorySubjectRepository.class);
        MemoryRecordRepository records=mock(MemoryRecordRepository.class);
        MemoryCandidateRepository candidates=mock(MemoryCandidateRepository.class);
        MemoryConversationSnapshotRepository snapshots=mock(MemoryConversationSnapshotRepository.class);
        MemorySemanticRetrievalService semantic=mock(MemorySemanticRetrievalService.class);
        OrganizationRetentionPolicyRepository policies=mock(OrganizationRetentionPolicyRepository.class);
        OrganizationRetentionPolicyEntity held=new OrganizationRetentionPolicyEntity("org-a");
        held.update(null, null, null, null, true, "TEST", "hold", "auditor", Instant.now(), null);
        when(policies.findById("org-a")).thenReturn(Optional.of(held));
        MemoryLifecycleService service=service(subjects, records, candidates, snapshots, semantic, policies, mock(AuditService.class));

        assertThatThrownBy(() -> service.deleteSubject("org-a", "app-a", "EXTERNAL_USER", "subject-a", "agent-a", "actor-a", "delete"))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Legal hold");
        MemoryRecordEntity expired=record(8L, Instant.now().minusSeconds(30));
        when(records.findByStatusInAndValidToBefore(any(), any())).thenReturn(List.of(expired));
        var expiry=service.expireDueRecords(Instant.now());
        assertThat(expiry.expired()).isZero();
        assertThat(expiry.skippedLegalHold()).isEqualTo(1);
        assertThat(expired.getStatus()).isEqualTo("ACTIVE");
    }

    @Test void expiryRemovesTheVectorAndMarksTheRelationalRecordUnreadable() throws Exception {
        MemoryRecordRepository records=mock(MemoryRecordRepository.class);
        MemorySemanticRetrievalService semantic=mock(MemorySemanticRetrievalService.class);
        MemoryRecordEntity expired=record(8L, Instant.now().minusSeconds(30));
        when(records.findByStatusInAndValidToBefore(any(), any())).thenReturn(List.of(expired));
        when(semantic.remove(expired)).thenReturn(true);
        OrganizationRetentionPolicyRepository policies=mock(OrganizationRetentionPolicyRepository.class);
        when(policies.findById("org-a")).thenReturn(Optional.empty());
        MemoryLifecycleService service=service(mock(MemorySubjectRepository.class), records, mock(MemoryCandidateRepository.class),
                mock(MemoryConversationSnapshotRepository.class), semantic, policies, mock(AuditService.class));

        var result=service.expireDueRecords(Instant.now());

        assertThat(result.expired()).isEqualTo(1);
        assertThat(result.vectorFailures()).isZero();
        assertThat(expired.getStatus()).isEqualTo("EXPIRED");
        verify(records).save(expired);
    }

    @Test void rejectsCrossAgentSubjectDeletionAndRevokesOnlyOwnedRecords() throws Exception {
        MemoryRecordRepository records=mock(MemoryRecordRepository.class);
        MemorySemanticRetrievalService semantic=mock(MemorySemanticRetrievalService.class);
        MemoryRecordEntity owned=record(8L, Instant.now().plusSeconds(60)); owned.assignAgent("agent-a");
        MemoryRecordEntity other=record(9L, Instant.now().plusSeconds(60)); other.assignAgent("agent-b");
        MemorySubjectRepository subjects=mock(MemorySubjectRepository.class);
        when(subjects.findByOrgIdAndApplicationCodeAndSubjectTypeAndExternalRef("org-a", "app-a", "EXTERNAL_USER", "subject-a"))
                .thenReturn(Optional.of(subject(7L, "subject-a")));
        when(records.findByOrgIdAndSubjectId("org-a", 7L)).thenReturn(List.of(owned, other));
        OrganizationRetentionPolicyRepository policies=mock(OrganizationRetentionPolicyRepository.class);
        when(policies.findById("org-a")).thenReturn(Optional.empty());
        MemoryLifecycleService service=service(subjects, records, mock(MemoryCandidateRepository.class), mock(MemoryConversationSnapshotRepository.class), semantic, policies, mock(AuditService.class));

        assertThatThrownBy(() -> service.deleteSubject("org-a", "app-a", "EXTERNAL_USER", "subject-a", "agent-a", "actor-a", "delete"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("exclusively owned");
        when(records.findByIdAndOrgIdAndAgentId(8L, "org-a", "agent-a")).thenReturn(Optional.of(owned));
        when(semantic.remove(owned)).thenReturn(false);

        assertThat(service.revokeRecord("org-a", "agent-a", 8L, "actor-a", "incorrect")).isFalse();
        assertThat(owned.getStatus()).isEqualTo("REVOKED");
        assertThat(owned.getContent()).isEqualTo("[deleted]");
    }

    private static MemoryLifecycleService service(MemorySubjectRepository subjects, MemoryRecordRepository records,
                                                  MemoryCandidateRepository candidates, MemoryConversationSnapshotRepository snapshots,
                                                  MemorySemanticRetrievalService semantic, OrganizationRetentionPolicyRepository policies,
                                                  AuditService audit) {
        return new MemoryLifecycleService(subjects, records, candidates, snapshots, semantic, policies, audit);
    }

    private static MemorySubjectEntity subject(long id, String ref) throws Exception {
        MemorySubjectEntity subject=new MemorySubjectEntity("org-a", "app-a", "EXTERNAL_USER", ref, "VERIFIED");
        setId(subject, id); return subject;
    }

    private static MemoryRecordEntity record(long id, Instant validTo) throws Exception {
        MemoryRecordEntity record=new MemoryRecordEntity("org-a", 7L, "SUBJECT_SHARED", null, "SUMMARY", "private text",
                "ACTIVE", "NORMAL", BigDecimal.ONE, Instant.now().minusSeconds(300), validTo, "HUMAN", "[]");
        setId(record, id); return record;
    }

    private static void setId(Object entity, long id) throws Exception {
        Field field=entity.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(entity, id);
    }
}
