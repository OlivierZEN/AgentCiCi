package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbEvalRunRepository extends JpaRepository<KbEvalRunEntity, Long> {

    List<KbEvalRunEntity> findTop20ByOrgIdAndSuiteIdOrderByIdDesc(String orgId, Long suiteId);

    Optional<KbEvalRunEntity> findByIdAndOrgId(Long id, String orgId);
}
