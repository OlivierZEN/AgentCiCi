package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbRetrievalLogRepository extends JpaRepository<KbRetrievalLogEntity, Long> {

    List<KbRetrievalLogEntity> findTop50ByCompanyIdAndKnowledgeBaseIdOrderByIdDesc(String companyId, Long knowledgeBaseId);
}
