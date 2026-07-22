package com.codehouse.ciciassistant.memory.service;

import com.codehouse.ciciassistant.memory.domain.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Governs generic memory proposals; only an explicit review can create readable memory. */
@Service
public class MemoryCandidateGovernanceService {
    private final ExternalMemoryContextService contextService; private final MemoryCandidateRepository candidates; private final MemoryRecordRepository records;
    public MemoryCandidateGovernanceService(ExternalMemoryContextService contextService, MemoryCandidateRepository candidates, MemoryRecordRepository records) { this.contextService=contextService; this.candidates=candidates; this.records=records; }
    @Transactional public MemoryCandidateEntity submit(ExternalMemoryContextService.ExternalMemoryContext context, ExternalMemoryContextService.MemoryWriteCommand command) {
        if (command == null) throw new IllegalArgumentException("memory candidate command is required");
        MemorySubjectEntity subject=contextService.resolveSubject(context);
        if ("ACTIVE".equals(command.status()) || "VERIFIED".equals(command.status())) throw new IllegalArgumentException("candidates cannot be submitted as readable memory");
        return candidates.save(new MemoryCandidateEntity(context.orgId(), subject.getId(), command.scope(), command.scopeKey(), command.memoryType(), command.content(), command.sensitivity(), command.confidence()==null?BigDecimal.ONE:command.confidence(), command.validFrom()==null?Instant.now():command.validFrom(), command.validTo(), command.sourceType(), command.sourceRefsJson()==null?"[]":command.sourceRefsJson()));
    }
    @Transactional public MemoryRecordEntity approve(String orgId, Long candidateId, String reviewer, String reason) {
        MemoryCandidateEntity candidate=candidates.findByIdAndOrgId(candidateId, orgId).orElseThrow(() -> new IllegalArgumentException("memory candidate not found"));
        if (!"PENDING".equals(candidate.getStatus())) throw new IllegalStateException("memory candidate is not pending");
        candidate.review("APPROVED", reviewer, reason); candidates.save(candidate);
        return records.save(new MemoryRecordEntity(orgId, candidate.getSubjectId(), candidate.getScope(), candidate.getScopeKey(), candidate.getMemoryType(), candidate.getContent(), "ACTIVE", candidate.getSensitivity(), candidate.getConfidence(), candidate.getValidFrom(), candidate.getValidTo(), candidate.getSourceType(), candidate.getSourceRefsJson()));
    }
    @Transactional public void reject(String orgId, Long candidateId, String reviewer, String reason) { MemoryCandidateEntity c=candidates.findByIdAndOrgId(candidateId, orgId).orElseThrow(() -> new IllegalArgumentException("memory candidate not found")); if (!"PENDING".equals(c.getStatus())) throw new IllegalStateException("memory candidate is not pending"); c.review("REJECTED", reviewer, reason); candidates.save(c); }
}
