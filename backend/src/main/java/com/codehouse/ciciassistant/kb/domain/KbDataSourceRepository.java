package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbDataSourceRepository extends JpaRepository<KbDataSourceEntity, Long> {

    List<KbDataSourceEntity> findByOrgIdAndKnowledgeBaseIdOrderByIdDesc(String orgId, Long knowledgeBaseId);

    Optional<KbDataSourceEntity> findByIdAndOrgId(Long id, String orgId);
}
