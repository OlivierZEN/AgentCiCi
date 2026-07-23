package com.codehouse.ciciassistant.memory.service;

import com.codehouse.ciciassistant.memory.domain.*;
import com.codehouse.ciciassistant.ops.service.AuditService;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Governs generic memory proposals; only an explicit review can create readable memory. */
@Service
public class MemoryCandidateGovernanceService {
    private final ExternalMemoryContextService contextService; private final MemoryCandidateRepository candidates; private final MemoryRecordRepository records; private final MemorySemanticRetrievalService semanticRetrieval; private final AuditService audit;
    public MemoryCandidateGovernanceService(ExternalMemoryContextService contextService, MemoryCandidateRepository candidates, MemoryRecordRepository records, MemorySemanticRetrievalService semanticRetrieval, AuditService audit) { this.contextService=contextService; this.candidates=candidates; this.records=records; this.semanticRetrieval=semanticRetrieval; this.audit=audit; }
    @Transactional public MemoryCandidateEntity submit(ExternalMemoryContextService.ExternalMemoryContext context, String agentId, ExternalMemoryContextService.MemoryWriteCommand command) {
        if (command == null) throw new IllegalArgumentException("memory candidate command is required");
        if (agentId == null || agentId.isBlank()) throw new IllegalArgumentException("agentId is required");
        MemorySubjectEntity subject=contextService.resolveSubject(context);
        if ("ACTIVE".equals(command.status()) || "VERIFIED".equals(command.status())) throw new IllegalArgumentException("candidates cannot be submitted as readable memory");
        return candidates.save(new MemoryCandidateEntity(context.orgId(), agentId.trim(), subject.getId(), command.scope(), command.scopeKey(), command.memoryType(), command.content(), command.sensitivity(), command.confidence()==null?BigDecimal.ONE:command.confidence(), command.validFrom()==null?Instant.now():command.validFrom(), command.validTo(), command.sourceType(), command.sourceRefsJson()==null?"[]":command.sourceRefsJson()));
    }
    @Transactional public MemoryRecordEntity approve(String orgId, String agentId, Long candidateId, String reviewer, String reason) {
        MemoryCandidateEntity candidate=requireCandidate(orgId, agentId, candidateId);
        if (!"PENDING".equals(candidate.getStatus())) throw new IllegalStateException("memory candidate is not pending");
        candidate.review("APPROVED", reviewer, reason); candidates.save(candidate);
        MemoryRecordEntity record=records.save(new MemoryRecordEntity(orgId, candidate.getSubjectId(), candidate.getScope(), candidate.getScopeKey(), candidate.getMemoryType(), candidate.getContent(), "ACTIVE", candidate.getSensitivity(), candidate.getConfidence(), candidate.getValidFrom(), candidate.getValidTo(), candidate.getSourceType(), candidate.getSourceRefsJson()));
        semanticRetrieval.index(record);
        audit.log(orgId, reviewer, "agent.memory.candidate.approve", "agent=" + agentId + ",candidateId=" + candidateId);
        return record;
    }
    @Transactional public void reject(String orgId, String agentId, Long candidateId, String reviewer, String reason) { MemoryCandidateEntity c=requireCandidate(orgId, agentId, candidateId); if (!"PENDING".equals(c.getStatus())) throw new IllegalStateException("memory candidate is not pending"); c.review("REJECTED", reviewer, reason); candidates.save(c); audit.log(orgId, reviewer, "agent.memory.candidate.reject", "agent=" + agentId + ",candidateId=" + candidateId); }
    @Transactional(readOnly=true) public java.util.List<MemoryCandidateEntity> list(String orgId, String agentId) { return candidates.findByOrgIdAndAgentIdOrderByUpdatedAtDesc(orgId, agentId); }
    private MemoryCandidateEntity requireCandidate(String orgId, String agentId, Long candidateId) { return candidates.findByIdAndOrgIdAndAgentId(candidateId, orgId, agentId).orElseThrow(() -> new IllegalArgumentException("memory candidate not found")); }
}
