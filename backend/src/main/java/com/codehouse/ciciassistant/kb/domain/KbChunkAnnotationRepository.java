package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbChunkAnnotationRepository extends JpaRepository<KbChunkAnnotationEntity, Long> {

    List<KbChunkAnnotationEntity> findTop100ByOrgIdAndKnowledgeBaseIdOrderByUpdatedAtDesc(String orgId, Long knowledgeBaseId);

    Optional<KbChunkAnnotationEntity> findByOrgIdAndChunkIdAndFieldKey(String orgId, Long chunkId, String fieldKey);
}
