package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbEvalCaseResultRepository extends JpaRepository<KbEvalCaseResultEntity, Long> {

    List<KbEvalCaseResultEntity> findByOrgIdAndRunIdOrderByIdAsc(String orgId, Long runId);
}
