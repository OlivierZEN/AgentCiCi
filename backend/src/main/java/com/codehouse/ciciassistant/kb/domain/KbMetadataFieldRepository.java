package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbMetadataFieldRepository extends JpaRepository<KbMetadataFieldEntity, Long> {

    List<KbMetadataFieldEntity> findByOrgIdAndKnowledgeBaseIdOrderByIdAsc(String orgId, Long knowledgeBaseId);

    Optional<KbMetadataFieldEntity> findByOrgIdAndKnowledgeBaseIdAndFieldKey(String orgId, Long knowledgeBaseId, String fieldKey);
}
