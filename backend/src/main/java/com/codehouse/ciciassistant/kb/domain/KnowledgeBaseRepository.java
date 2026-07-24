package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KnowledgeBaseRepository extends JpaRepository<KnowledgeBaseEntity, Long> {

    List<KnowledgeBaseEntity> findByCompanyIdOrderByIdDesc(String companyId);

    List<KnowledgeBaseEntity> findByCompanyIdAndStatusNotOrderByIdDesc(String companyId, String status);

    List<KnowledgeBaseEntity> findByCompanyIdAndIdIn(String companyId, List<Long> ids);

    Optional<KnowledgeBaseEntity> findByIdAndCompanyId(Long id, String companyId);

    void deleteByIdAndCompanyId(Long id, String companyId);
}
