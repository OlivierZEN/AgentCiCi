package com.codehouse.ciciassistant.memory.service;

import com.codehouse.ciciassistant.memory.domain.MemoryCandidateEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryCandidateRepository;
import com.codehouse.ciciassistant.memory.domain.MemoryConversationSnapshotRepository;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordEntity;
import com.codehouse.ciciassistant.memory.domain.MemoryRecordRepository;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectEntity;
import com.codehouse.ciciassistant.memory.domain.MemorySubjectRepository;
import com.codehouse.ciciassistant.ops.service.AuditService;
import com.codehouse.ciciassistant.platform.domain.CompanyRetentionPolicyRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Applies deletion and expiry without making vector availability an authorization fact. */
@Service
public class MemoryLifecycleService {
    private static final List<String> READABLE_STATUSES = List.of("ACTIVE", "VERIFIED");
    private final MemorySubjectRepository subjects;
    private final MemoryRecordRepository records;
    private final MemoryCandidateRepository candidates;
    private final MemoryConversationSnapshotRepository snapshots;
    private final MemorySemanticRetrievalService semantic;
    private final CompanyRetentionPolicyRepository retentionPolicies;
    private final JdbcTemplate jdbcTemplate;
    private final AuditService audit;

    public MemoryLifecycleService(MemorySubjectRepository subjects, MemoryRecordRepository records,
                                  MemoryCandidateRepository candidates, MemoryConversationSnapshotRepository snapshots,
                                  MemorySemanticRetrievalService semantic,
                                  CompanyRetentionPolicyRepository retentionPolicies, JdbcTemplate jdbcTemplate, AuditService audit) {
        this.subjects=subjects; this.records=records; this.candidates=candidates; this.snapshots=snapshots;
        this.semantic=semantic; this.retentionPolicies=retentionPolicies; this.jdbcTemplate=jdbcTemplate; this.audit=audit;
    }

    @Transactional
    public DeletionResult deleteSubject(String companyId, String applicationCode, String subjectType,
                                        String externalSubjectRef, String agentId, String actorUserId, String reason) {
        requireDeletionAllowed(companyId);
        MemorySubjectEntity subject=subjects.findByCompanyIdAndApplicationCodeAndSubjectTypeAndExternalRef(
                        required(companyId, "companyId"), required(applicationCode, "applicationCode"),
                        required(subjectType, "subjectType"), required(externalSubjectRef, "externalSubjectRef"))
                .orElseThrow(() -> new IllegalArgumentException("Memory subject not found"));
        List<MemoryRecordEntity> subjectRecords=records.findByCompanyIdAndSubjectId(companyId, subject.getId());
        if (subjectRecords.stream().anyMatch(record -> !agentId.equals(record.getAgentId()))) {
            throw new IllegalArgumentException("Memory subject is not exclusively owned by the target agent");
        }
        int vectorFailures=0;
        for (MemoryRecordEntity record:subjectRecords) {
            if (!semantic.remove(record)) vectorFailures++;
            record.revokeAndRedact();
        }
        records.saveAll(subjectRecords);
        List<MemoryCandidateEntity> subjectCandidates=candidates.findByCompanyIdAndSubjectId(companyId, subject.getId());
        subjectCandidates.forEach(MemoryCandidateEntity::revokeAndRedact);
        candidates.saveAll(subjectCandidates);
        int redactedEvidence=redactEvidence(companyId, subject.getId());
        long removedSnapshots=snapshots.deleteByCompanyIdAndSubjectId(companyId, subject.getId());
        subject.anonymize(); subjects.save(subject);
        audit.log(companyId, actorUserId, "agent.memory.subject.delete",
                "application=" + applicationCode + ",subjectId=" + subject.getId() + ",records=" + subjectRecords.size()
                        + ",candidates=" + subjectCandidates.size() + ",snapshots=" + removedSnapshots
                        + ",evidence=" + redactedEvidence + ",vectorFailures=" + vectorFailures + ",reason=" + safeReason(reason));
        return new DeletionResult(subjectRecords.size(), subjectCandidates.size(), redactedEvidence, removedSnapshots, vectorFailures);
    }

    @Transactional
    public boolean revokeRecord(String companyId, String agentId, Long recordId, String actorUserId, String reason) {
        requireDeletionAllowed(companyId);
        MemoryRecordEntity record=records.findByIdAndCompanyIdAndAgentId(recordId, companyId, agentId)
                .orElseThrow(() -> new IllegalArgumentException("Memory record not found"));
        if (!READABLE_STATUSES.contains(record.getStatus())) throw new IllegalStateException("Memory record is not readable");
        boolean vectorRemoved=semantic.remove(record); record.revokeAndRedact(); records.save(record);
        audit.log(companyId, actorUserId, "agent.memory.record.revoke", "agent=" + agentId + ",recordId=" + recordId + ",vectorRemoved=" + vectorRemoved + ",reason=" + safeReason(reason));
        return vectorRemoved;
    }

    @Scheduled(fixedDelayString = "${app.memory.expiry-worker-delay-ms:3600000}", initialDelayString = "${app.memory.expiry-worker-initial-delay-ms:120000}")
    @Transactional
    public void expireDueRecords() { expireDueRecords(Instant.now()); }

    @Transactional
    public ExpiryResult expireDueRecords(Instant now) {
        Instant cutoff=now == null ? Instant.now() : now;
        int expired=0, vectorFailures=0, skippedLegalHold=0;
        for (MemoryRecordEntity record:records.findByStatusInAndValidToBefore(READABLE_STATUSES, cutoff)) {
            if (retentionPolicies.findById(record.getCompanyId()).map(policy -> policy.isLegalHold()).orElse(false)) {
                skippedLegalHold++; continue;
            }
            if (!semantic.remove(record)) vectorFailures++;
            record.markExpired(); records.save(record); expired++;
        }
        return new ExpiryResult(expired, vectorFailures, skippedLegalHold);
    }

    private void requireDeletionAllowed(String companyId) {
        if (retentionPolicies.findById(companyId).map(policy -> policy.isLegalHold()).orElse(false)) {
            throw new IllegalStateException("Legal hold is active; memory deletion is blocked");
        }
    }
    private int redactEvidence(String companyId, Long subjectId) {
        return jdbcTemplate.update("""
                UPDATE memory_evidence
                SET reference_value = '[deleted]', excerpt = NULL
                WHERE company_id = ? AND (
                    candidate_id IN (SELECT id FROM memory_candidate WHERE company_id = ? AND subject_id = ?)
                    OR memory_record_id IN (SELECT id FROM memory_record WHERE company_id = ? AND subject_id = ?)
                )
                """, companyId, companyId, subjectId, companyId, subjectId);
    }
    private String required(String raw, String field) { String value=raw == null ? "" : raw.trim(); if (value.isBlank()) throw new IllegalArgumentException(field + " is required"); return value; }
    private String safeReason(String raw) { String value=raw == null ? "" : raw.trim(); return value.isBlank() ? "unspecified" : value.substring(0, Math.min(value.length(), 160)); }
    public record DeletionResult(int records, int candidates, int evidence, long snapshots, int vectorFailures) {}
    public record ExpiryResult(int expired, int vectorFailures, int skippedLegalHold) {}
}
