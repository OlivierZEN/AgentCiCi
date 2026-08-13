package com.codehouse.ciciassistant.memory.service;

import com.codehouse.ciciassistant.kb.service.*;
import com.codehouse.ciciassistant.memory.domain.*;
import com.codehouse.ciciassistant.security.service.SecurityRedactionService;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Vector matches are candidates only; relational context authorization remains authoritative. */
@Service
public class MemorySemanticRetrievalService {
    private static final String COLLECTION_SCOPE = "agent-memory";
    private final EmbeddingService embeddings; private final VectorStoreClient vectors; private final MemoryVectorFragmentRepository fragments; private final MemoryRecordRepository records; private final ExternalMemoryContextService contexts; private final SecurityRedactionService redaction;
    public MemorySemanticRetrievalService(EmbeddingService embeddings, VectorStoreClient vectors, MemoryVectorFragmentRepository fragments, MemoryRecordRepository records, ExternalMemoryContextService contexts, SecurityRedactionService redaction) { this.embeddings=embeddings; this.vectors=vectors; this.fragments=fragments; this.records=records; this.contexts=contexts; this.redaction=redaction; }
    @Transactional public void index(MemoryRecordEntity record) {
        if (record == null || record.getId() == null || !Set.of("ACTIVE", "VERIFIED").contains(record.getStatus()) || !"NORMAL".equals(record.getSensitivity())) return;
        String safeText=redaction.redact(record.getContent());
        if (safeText.isBlank()) return;
        String vectorId=vectors.upsert(new VectorUpsertCommand(record.getCompanyId(), COLLECTION_SCOPE, record.getId(), record.getId(), 0, safeText, Integer.toHexString(safeText.hashCode()), embeddings.embedForScene(record.getCompanyId(), "memory-embedding", null, safeText)));
        fragments.save(new MemoryVectorFragmentEntity(record.getCompanyId(), record.getId(), vectorId, safeText));
    }
    @Transactional public boolean remove(MemoryRecordEntity record) {
        if (record == null || record.getId() == null) return true;
        return fragments.findByCompanyIdAndMemoryRecordIdAndStatus(record.getCompanyId(), record.getId(), "ACTIVE").map(fragment -> {
            try {
                VectorDeleteResult result=vectors.deleteByVectorIds(record.getCompanyId(), List.of(fragment.getVectorId()));
                if (!result.success()) return false;
                fragment.markDeleted(); fragments.save(fragment); return true;
            } catch (RuntimeException ignored) { return false; }
        }).orElse(true);
    }
    @Transactional(readOnly = true) public List<MemoryRecordEntity> retrieve(ExternalMemoryContextService.ExternalMemoryContext context, String agentId, Set<String> namespaces, String query, int topK) {
        if (query == null || query.isBlank()) return List.of();
        List<MemoryRecordEntity> allowed=contexts.loadContext(context, agentId, namespaces, null).records();
        Set<Long> allowedIds=new HashSet<>(); for (MemoryRecordEntity record:allowed) allowedIds.add(record.getId());
        return vectors.search(new VectorSearchQuery(context.companyId(), List.of(COLLECTION_SCOPE), query, embeddings.embedForScene(context.companyId(), "memory-embedding", null, query), Math.max(1, Math.min(topK, 16)))).stream().map(VectorSearchHit::chunkId).filter(Objects::nonNull).filter(allowedIds::contains).map(records::findById).flatMap(Optional::stream).toList();
    }
}
