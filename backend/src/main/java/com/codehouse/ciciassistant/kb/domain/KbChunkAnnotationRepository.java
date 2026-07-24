package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbChunkAnnotationRepository extends JpaRepository<KbChunkAnnotationEntity, Long> {

    List<KbChunkAnnotationEntity> findTop100ByCompanyIdAndKnowledgeBaseIdOrderByUpdatedAtDesc(String companyId, Long knowledgeBaseId);

    Optional<KbChunkAnnotationEntity> findByCompanyIdAndChunkIdAndFieldKey(String companyId, Long chunkId, String fieldKey);
}
