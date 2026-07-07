package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbAnnotationSuggestionRepository extends JpaRepository<KbAnnotationSuggestionEntity, Long> {

    List<KbAnnotationSuggestionEntity> findTop100ByOrgIdAndKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(
            String orgId, Long knowledgeBaseId, String status);

    Optional<KbAnnotationSuggestionEntity> findByIdAndOrgId(Long id, String orgId);
}
