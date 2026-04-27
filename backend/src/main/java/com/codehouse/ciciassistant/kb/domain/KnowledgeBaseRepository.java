package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    List<KnowledgeBaseEntity> findByOrgIdOrderByIdDesc(String orgId);

    Optional<KnowledgeBaseEntity> findByIdAndOrgId(Long id, String orgId);

    void deleteByIdAndOrgId(Long id, String orgId);
}
