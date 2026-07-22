package com.codehouse.ciciassistant.memory.service;

import com.codehouse.ciciassistant.memory.domain.MemoryConversationSnapshotEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryConversationSnapshotRepository;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordRepository;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectEntity;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Generic memory core for a trusted external application integration. This service deliberately has no
 * domain-specific concepts: applications own their business facts and pass only normalized subject,
 * conversation and authorization context to Agent CC.
 */
@Service
public class ExternalMemoryContextService {

    private static final Set<String> SUBJECT_TYPES = Set.of("EXTERNAL_USER", "EXTERNAL_PRINCIPAL");
    private static final Set<String> IDENTITY_LEVELS = Set.of("ANONYMOUS", "VERIFIED");
    private static final Set<String> SCOPES = Set.of(
            "CONVERSATION", "SUBJECT_SHARED", "AGENT_PRIVATE", "DOMAIN_NAMESPACE");
    private static final Set<String> MEMORY_TYPES = Set.of(
            "PREFERENCE", "VERIFIED_FACT", "DOMAIN_STATE", "COMMITMENT",
            "OPEN_ITEM", "ROUTING", "RESTRICTION", "SUMMARY");
    private static final Set<String> STATUSES = Set.of(
            "CANDIDATE", "VERIFIED", "ACTIVE", "RESOLVED", "EXPIRED", "SUPERSEDED", "REVOKED");
    private static final Set<String> SENSITIVITIES = Set.of("NORMAL", "INTERNAL", "SENSITIVE");
    private static final Set<String> SOURCE_TYPES = Set.of(
            "EXTERNAL_MESSAGE", "AGENT_REPLY", "TOOL_RESULT", "DOMAIN_SYSTEM", "HUMAN");
    private static final int MAX_CONTEXT_RECORDS = 16;
    private static final int MAX_SUMMARY_LENGTH = 8_000;

    private final MemorySubjectRepository subjectRepository;
    private final MemoryRecordRepository recordRepository;
    private final MemoryConversationSnapshotRepository snapshotRepository;

    public ExternalMemoryContextService(MemorySubjectRepository subjectRepository,
                                        MemoryRecordRepository recordRepository,
                                        MemoryConversationSnapshotRepository snapshotRepository) {
        this.subjectRepository = subjectRepository;
        this.recordRepository = recordRepository;
        this.snapshotRepository = snapshotRepository;
    }

    @Transactional
    public MemorySubjectEntity resolveSubject(ExternalMemoryContext context) {
        validateContext(context);
        return subjectRepository.findByOrgIdAndApplicationCodeAndSubjectTypeAndExternalRef(
                        context.orgId(), context.applicationCode(), context.subjectType(), context.externalSubjectRef())
                .orElseGet(() -> subjectRepository.save(new MemorySubjectEntity(
                        context.orgId(), context.applicationCode(), context.subjectType(),
                        context.externalSubjectRef(), context.identityLevel())));
    }

    @Transactional
    public MemoryConversationSnapshotEntity upsertSnapshot(ExternalMemoryContext context,
                                                            String activeAgentId,
                                                            String summary,
                                                            String stateJson) {
        MemorySubjectEntity subject = resolveSubject(context);
        String safeSummary = requiredText(summary, "summary");
        if (safeSummary.length() > MAX_SUMMARY_LENGTH) {
            throw new IllegalArgumentException("summary exceeds maximum length");
        }
        String safeState = blankToDefault(stateJson, "{}");
        return snapshotRepository.findByOrgIdAndApplicationCodeAndConversationRef(
                        context.orgId(), context.applicationCode(), context.conversationRef())
                .map(existing -> {
                    existing.update(trimToNull(activeAgentId), safeSummary, safeState);
                    return snapshotRepository.save(existing);
                })
                .orElseGet(() -> snapshotRepository.save(new MemoryConversationSnapshotEntity(
                        context.orgId(), context.applicationCode(), context.conversationRef(), subject.getId(),
                        trimToNull(activeAgentId), safeSummary, safeState)));
    }

    @Transactional
    public MemoryRecordEntity writeRecord(ExternalMemoryContext context, MemoryWriteCommand command) {
        MemorySubjectEntity subject = resolveSubject(context);
        validateWriteCommand(command);
        return recordRepository.save(new MemoryRecordEntity(
                context.orgId(), subject.getId(), command.scope(), trimToNull(command.scopeKey()),
                command.memoryType(), requiredText(command.content(), "content"), command.status(),
                command.sensitivity(), command.confidence() == null ? BigDecimal.ONE : command.confidence(),
                command.validFrom() == null ? Instant.now() : command.validFrom(), command.validTo(),
                command.sourceType(), blankToDefault(command.sourceRefsJson(), "[]")));
    }

    @Transactional(readOnly = true)
    public MemoryContext loadContext(ExternalMemoryContext context, String agentId,
                                     Set<String> grantedDomainNamespaces, Instant now) {
        validateContext(context);
        MemorySubjectEntity subject = requireExistingSubject(context);
        Instant effectiveNow = now == null ? Instant.now() : now;
        Set<String> namespaces = grantedDomainNamespaces == null ? Set.of() : new LinkedHashSet<>(grantedDomainNamespaces);
        List<MemoryRecordEntity> records = recordRepository
                .findByOrgIdAndSubjectIdAndStatusInOrderByUpdatedAtDesc(context.orgId(), subject.getId(), List.of("ACTIVE", "VERIFIED"))
                .stream()
                .filter(item -> isCurrent(item, effectiveNow))
                .filter(item -> isVisible(item, context.conversationRef(), agentId, namespaces))
                .limit(MAX_CONTEXT_RECORDS)
                .toList();
        Optional<MemoryConversationSnapshotEntity> snapshot = snapshotRepository
                .findByOrgIdAndApplicationCodeAndConversationRef(
                        context.orgId(), context.applicationCode(), context.conversationRef());
        return new MemoryContext(subject.getId(), snapshot.map(MemoryConversationSnapshotEntity::getSummary).orElse(""), records);
    }

    private MemorySubjectEntity requireExistingSubject(ExternalMemoryContext context) {
        return subjectRepository.findByOrgIdAndApplicationCodeAndSubjectTypeAndExternalRef(
                        context.orgId(), context.applicationCode(), context.subjectType(), context.externalSubjectRef())
                .orElseThrow(() -> new IllegalArgumentException("external memory subject is not registered"));
    }

    private static boolean isCurrent(MemoryRecordEntity item, Instant now) {
        return !item.getValidFrom().isAfter(now)
                && (item.getValidTo() == null || !item.getValidTo().isBefore(now));
    }

    private static boolean isVisible(MemoryRecordEntity item, String conversationRef, String agentId,
                                     Set<String> grantedDomainNamespaces) {
        return switch (item.getScope()) {
            case "SUBJECT_SHARED" -> true;
            case "CONVERSATION" -> Objects.equals(item.getScopeKey(), conversationRef);
            case "AGENT_PRIVATE" -> Objects.equals(item.getScopeKey(), agentId);
            case "DOMAIN_NAMESPACE" -> item.getScopeKey() != null && grantedDomainNamespaces.contains(item.getScopeKey());
            default -> false;
        };
    }

    private static void validateContext(ExternalMemoryContext context) {
        if (context == null) {
            throw new IllegalArgumentException("external memory context is required");
        }
        requiredText(context.orgId(), "orgId");
        requiredText(context.applicationCode(), "applicationCode");
        requiredText(context.conversationRef(), "conversationRef");
        requiredText(context.externalSubjectRef(), "externalSubjectRef");
        requireEnum(SUBJECT_TYPES, context.subjectType(), "subjectType");
        requireEnum(IDENTITY_LEVELS, context.identityLevel(), "identityLevel");
    }

    private static void validateWriteCommand(MemoryWriteCommand command) {
        if (command == null) {
            throw new IllegalArgumentException("memory write command is required");
        }
        requireEnum(SCOPES, command.scope(), "scope");
        if (!"SUBJECT_SHARED".equals(command.scope()) && trimToNull(command.scopeKey()) == null) {
            throw new IllegalArgumentException("scopeKey is required for non-shared memory");
        }
        requireEnum(MEMORY_TYPES, command.memoryType(), "memoryType");
        requireEnum(STATUSES, command.status(), "status");
        requireEnum(SENSITIVITIES, command.sensitivity(), "sensitivity");
        requireEnum(SOURCE_TYPES, command.sourceType(), "sourceType");
        requiredText(command.content(), "content");
        if (command.validTo() != null && command.validFrom() != null && command.validTo().isBefore(command.validFrom())) {
            throw new IllegalArgumentException("validTo must not be before validFrom");
        }
    }

    private static void requireEnum(Set<String> values, String value, String field) {
        if (value == null || !values.contains(value.trim())) {
            throw new IllegalArgumentException("invalid " + field);
        }
    }

    private static String requiredText(String value, String field) {
        String normalized = trimToNull(value);
        if (normalized == null) {
            throw new IllegalArgumentException(field + " is required");
        }
        return normalized;
    }

    private static String blankToDefault(String value, String fallback) {
        String normalized = trimToNull(value);
        return normalized == null ? fallback : normalized;
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String normalized = value.trim();
        return normalized.isEmpty() ? null : normalized;
    }

    public record ExternalMemoryContext(String orgId, String applicationCode, String conversationRef,
                                        String externalSubjectRef, String subjectType, String identityLevel) {
    }

    public record MemoryWriteCommand(String scope, String scopeKey, String memoryType, String content,
                                     String status, String sensitivity, BigDecimal confidence, Instant validFrom,
                                     Instant validTo, String sourceType, String sourceRefsJson) {
    }

    public record MemoryContext(Long subjectId, String summary, List<MemoryRecordEntity> records) {
    }
}
