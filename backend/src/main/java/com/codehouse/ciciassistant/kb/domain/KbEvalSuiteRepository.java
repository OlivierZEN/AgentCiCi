package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbEvalSuiteRepository extends JpaRepository<KbEvalSuiteEntity, Long> {

    List<KbEvalSuiteEntity> findByCompanyIdAndKnowledgeBaseIdAndStatusOrderByIdDesc(String companyId, Long knowledgeBaseId, String status);

    Optional<KbEvalSuiteEntity> findByIdAndCompanyId(Long id, String companyId);
}
