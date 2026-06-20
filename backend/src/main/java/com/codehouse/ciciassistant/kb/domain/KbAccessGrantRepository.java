package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbAccessGrantRepository extends JpaRepository<KbAccessGrantEntity, String> {

    List<KbAccessGrantEntity> findByOrgIdAndKnowledgeBaseIdAndDocumentIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
            String orgId,
            Long knowledgeBaseId,
            Long documentId,
            String targetType,
            String status);

    List<KbAccessGrantEntity> findByOrgIdAndKnowledgeBaseIdAndChunkIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
            String orgId,
            Long knowledgeBaseId,
            Long chunkId,
            String targetType,
            String status);
}
