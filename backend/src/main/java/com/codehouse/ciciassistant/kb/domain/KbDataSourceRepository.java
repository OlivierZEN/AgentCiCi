package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDataSourceRepository extends JpaRepository<KbDataSourceEntity, Long> {

    List<KbDataSourceEntity> findByCompanyIdOrderByIdDesc(String companyId);

    List<KbDataSourceEntity> findByCompanyIdAndKnowledgeBaseIdOrderByIdDesc(String companyId, Long knowledgeBaseId);

    Optional<KbDataSourceEntity> findByIdAndCompanyId(Long id, String companyId);
}
