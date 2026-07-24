package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbMetadataFieldRepository extends JpaRepository<KbMetadataFieldEntity, Long> {

    List<KbMetadataFieldEntity> findByCompanyIdAndKnowledgeBaseIdOrderByIdAsc(String companyId, Long knowledgeBaseId);

    Optional<KbMetadataFieldEntity> findByCompanyIdAndKnowledgeBaseIdAndFieldKey(String companyId, Long knowledgeBaseId, String fieldKey);
}
