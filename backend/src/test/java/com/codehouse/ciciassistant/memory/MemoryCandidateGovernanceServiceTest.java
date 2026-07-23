package com.codehouse.ciciassistant.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.memory.domain.MemoryCandidateEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryCandidateRepository;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordRepository;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectEntity;
import com.codehouse.ciciassistant.memory.service.ExternalMemoryContextService;
import com.codehouse.ciciassistant.memory.service.MemoryCandidateGovernanceService;
import com.codehouse.ciciassistant.memory.service.MemorySemanticRetrievalService;
import com.codehouse.ciciassistant.ops.service.AuditService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class MemoryCandidateGovernanceServiceTest {
    private final ExternalMemoryContextService contextService = mock(ExternalMemoryContextService.class);
    private final MemoryCandidateRepository candidateRepository = mock(MemoryCandidateRepository.class);
    private final MemoryRecordRepository recordRepository = mock(MemoryRecordRepository.class);
    private final MemorySemanticRetrievalService semanticRetrieval = mock(MemorySemanticRetrievalService.class);
    private final AuditService audit = mock(AuditService.class);
    private final MemoryCandidateGovernanceService service = new MemoryCandidateGovernanceService(
            contextService, candidateRepository, recordRepository, semanticRetrieval, audit);

    @Test
    void rejectsAnAttemptToSubmitReadableMemoryWithoutReview() {
        ExternalMemoryContextService.MemoryWriteCommand command = command("ACTIVE");

        assertThatThrownBy(() -> service.submit(context(), "agent-a", command))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be submitted");
    }

    @Test
    void approvalCreatesTheOnlyReadableRecordFromAPendingCandidate() throws Exception {
        MemoryCandidateEntity candidate = new MemoryCandidateEntity("org-a", "agent-a", 7L, "SUBJECT_SHARED", null,
                "PREFERENCE", "brief preference", "NORMAL", BigDecimal.ONE, Instant.now(), null, "HUMAN", "[]");
        setId(candidate, 3L);
        when(candidateRepository.findByIdAndOrgIdAndAgentId(3L, "org-a", "agent-a")).thenReturn(Optional.of(candidate));
        when(candidateRepository.save(any(MemoryCandidateEntity.class))).thenAnswer(i -> i.getArgument(0));
        when(recordRepository.save(any(MemoryRecordEntity.class))).thenAnswer(i -> i.getArgument(0));

        MemoryRecordEntity record = service.approve("org-a", "agent-a", 3L, "reviewer-1", "confirmed");

        assertThat(candidate.getStatus()).isEqualTo("APPROVED");
        assertThat(record.getStatus()).isEqualTo("ACTIVE");
        assertThat(record.getOrgId()).isEqualTo("org-a");
        verify(semanticRetrieval).index(record);
    }

    @Test
    void refusesASecondReviewOfTheSameCandidate() throws Exception {
        MemoryCandidateEntity candidate = new MemoryCandidateEntity("org-a", "agent-a", 7L, "SUBJECT_SHARED", null,
                "PREFERENCE", "brief preference", "NORMAL", BigDecimal.ONE, Instant.now(), null, "HUMAN", "[]");
        setId(candidate, 3L);
        candidate.review("REJECTED", "reviewer-1", "not supported");
        when(candidateRepository.findByIdAndOrgIdAndAgentId(3L, "org-a", "agent-a")).thenReturn(Optional.of(candidate));

        assertThatThrownBy(() -> service.approve("org-a", "agent-a", 3L, "reviewer-2", "retry"))
                .isInstanceOf(IllegalStateException.class);
    }

    private static ExternalMemoryContextService.ExternalMemoryContext context() { return new ExternalMemoryContextService.ExternalMemoryContext("org-a", "app-a", "conversation-a", "subject-a", "EXTERNAL_USER", "VERIFIED"); }
    private static ExternalMemoryContextService.MemoryWriteCommand command(String status) { return new ExternalMemoryContextService.MemoryWriteCommand("SUBJECT_SHARED", null, "PREFERENCE", "brief preference", status, "NORMAL", BigDecimal.ONE, Instant.now(), null, "HUMAN", "[]"); }
    private static void setId(Object value, Long id) throws Exception { Field field = value.getClass().getDeclaredField("id"); field.setAccessible(true); field.set(value, id); }
}
