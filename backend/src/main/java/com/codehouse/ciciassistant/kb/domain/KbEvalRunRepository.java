package com.codehouse.ciciassistant.kb.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface KbEvalRunRepository extends JpaRepository<KbEvalRunEntity, Long> {

    List<KbEvalRunEntity> findTop20ByCompanyIdAndSuiteIdOrderByIdDesc(String companyId, Long suiteId);

    Optional<KbEvalRunEntity> findByIdAndCompanyId(Long id, String companyId);
}
