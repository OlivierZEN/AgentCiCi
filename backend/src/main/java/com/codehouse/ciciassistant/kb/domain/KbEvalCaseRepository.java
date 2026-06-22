package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbEvalCaseRepository extends JpaRepository<KbEvalCaseEntity, Long> {

    List<KbEvalCaseEntity> findByOrgIdAndSuiteIdAndStatusOrderByIdAsc(String orgId, Long suiteId, String status);
}
