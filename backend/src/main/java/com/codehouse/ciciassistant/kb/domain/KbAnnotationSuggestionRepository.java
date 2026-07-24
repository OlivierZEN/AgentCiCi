package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbAnnotationSuggestionRepository extends JpaRepository<KbAnnotationSuggestionEntity, Long> {

    List<KbAnnotationSuggestionEntity> findTop100ByCompanyIdAndKnowledgeBaseIdAndStatusOrderByCreatedAtDesc(
            String companyId, Long knowledgeBaseId, String status);

    Optional<KbAnnotationSuggestionEntity> findByIdAndCompanyId(Long id, String companyId);
}
