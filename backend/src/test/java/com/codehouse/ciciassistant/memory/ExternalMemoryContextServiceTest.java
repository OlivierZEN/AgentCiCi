package com.codehouse.ciciassistant.memory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.codehouse.ciciassistant.memory.domain.MemoryConversationSnapshotRepository;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordRepository;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectEntity;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectRepository;
import com.codehouse.ciciassistant.memory.service.ExternalMemoryContextService;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class ExternalMemoryContextServiceTest {

    private final MemorySubjectRepository subjectRepository = mock(MemorySubjectRepository.class);
    private final MemoryRecordRepository recordRepository = mock(MemoryRecordRepository.class);
    private final MemoryConversationSnapshotRepository snapshotRepository = mock(MemoryConversationSnapshotRepository.class);
    private final ExternalMemoryContextService service = new ExternalMemoryContextService(
            subjectRepository, recordRepository, snapshotRepository);

    @Test
    void resolvesTheSameExternalReferenceIndependentlyPerApplication() {
        ExternalMemoryContextService.ExternalMemoryContext context = context("app-alpha", "subject-7");
        when(subjectRepository.findByOrgIdAndApplicationCodeAndSubjectTypeAndExternalRef(
                "org-a", "app-alpha", "EXTERNAL_USER", "subject-7")).thenReturn(Optional.empty());
        when(subjectRepository.save(any(MemorySubjectEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        MemorySubjectEntity created = service.resolveSubject(context);

        assertThat(created.getApplicationCode()).isEqualTo("app-alpha");
        assertThat(created.getExternalRef()).isEqualTo("subject-7");
        verify(subjectRepository).findByOrgIdAndApplicationCodeAndSubjectTypeAndExternalRef(
                "org-a", "app-alpha", "EXTERNAL_USER", "subject-7");
    }

    @Test
    void loadsOnlyCurrentAndAuthorizedScopesForTheExternalContext() throws Exception {
        MemorySubjectEntity subject = subject(7L, "app-alpha", "subject-7");
        when(subjectRepository.findByOrgIdAndApplicationCodeAndSubjectTypeAndExternalRef(
                "org-a", "app-alpha", "EXTERNAL_USER", "subject-7")).thenReturn(Optional.of(subject));
        Instant now = Instant.parse("2026-07-22T03:45:00Z");
        when(recordRepository.findByOrgIdAndSubjectIdAndStatusInOrderByUpdatedAtDesc(
                "org-a", 7L, List.of("ACTIVE", "VERIFIED"))).thenReturn(List.of(
                record(7L, "SUBJECT_SHARED", null, now.plusSeconds(3600)),
                record(7L, "CONVERSATION", "conversation-1", now.plusSeconds(3600)),
                record(7L, "AGENT_PRIVATE", "agent-a", now.plusSeconds(3600)),
                record(7L, "DOMAIN_NAMESPACE", "namespace-a", now.plusSeconds(3600)),
                record(7L, "AGENT_PRIVATE", "agent-b", now.plusSeconds(3600)),
                record(7L, "CONVERSATION", "conversation-2", now.plusSeconds(3600)),
                record(7L, "SUBJECT_SHARED", null, now.minusSeconds(1))));
        when(snapshotRepository.findByOrgIdAndApplicationCodeAndConversationRef(
                "org-a", "app-alpha", "conversation-1")).thenReturn(Optional.empty());

        ExternalMemoryContextService.MemoryContext result = service.loadContext(
                context("app-alpha", "subject-7"), "agent-a", Set.of("namespace-a"), now);

        assertThat(result.subjectId()).isEqualTo(7L);
        assertThat(result.records()).hasSize(4);
        assertThat(result.records()).extracting(MemoryRecordEntity::getScope)
                .containsExactly("SUBJECT_SHARED", "CONVERSATION", "AGENT_PRIVATE", "DOMAIN_NAMESPACE");
    }

    @Test
    void rejectsUnregisteredExternalSubjectsInsteadOfCreatingThemDuringRead() {
        when(subjectRepository.findByOrgIdAndApplicationCodeAndSubjectTypeAndExternalRef(
                "org-a", "app-alpha", "EXTERNAL_USER", "subject-7")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loadContext(context("app-alpha", "subject-7"), "agent-a", Set.of(), Instant.now()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not registered");
    }

    private static ExternalMemoryContextService.ExternalMemoryContext context(String applicationCode, String subjectRef) {
        return new ExternalMemoryContextService.ExternalMemoryContext(
                "org-a", applicationCode, "conversation-1", subjectRef, "EXTERNAL_USER", "VERIFIED");
    }

    private static MemorySubjectEntity subject(Long id, String applicationCode, String externalRef) throws Exception {
        MemorySubjectEntity subject = new MemorySubjectEntity(
                "org-a", applicationCode, "EXTERNAL_USER", externalRef, "VERIFIED");
        Field field = MemorySubjectEntity.class.getDeclaredField("id");
        field.setAccessible(true);
        field.set(subject, id);
        return subject;
    }

    private static MemoryRecordEntity record(Long subjectId, String scope, String scopeKey, Instant validTo) {
        return new MemoryRecordEntity("org-a", subjectId, scope, scopeKey, "SUMMARY", scope,
                "ACTIVE", "NORMAL", BigDecimal.ONE, Instant.parse("2026-07-22T00:00:00Z"), validTo,
                "HUMAN", "[]");
    }
}
