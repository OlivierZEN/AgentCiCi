package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbAccessGrantRepository extends JpaRepository<KbAccessGrantEntity, String> {

    List<KbAccessGrantEntity> findByCompanyIdAndKnowledgeBaseIdAndDocumentIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
            String companyId,
            Long knowledgeBaseId,
            Long documentId,
            String targetType,
            String status);

    List<KbAccessGrantEntity> findByCompanyIdAndKnowledgeBaseIdAndChunkIdAndTargetTypeAndStatusOrderByPrincipalTypeAscPrincipalIdAsc(
            String companyId,
            Long knowledgeBaseId,
            Long chunkId,
            String targetType,
            String status);
}
