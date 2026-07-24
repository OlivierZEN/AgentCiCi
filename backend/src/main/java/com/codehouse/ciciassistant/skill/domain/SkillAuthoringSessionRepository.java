package com.codehouse.ciciassistant.skill.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillAuthoringSessionRepository extends JpaRepository<SkillAuthoringSessionEntity, String> {

    Optional<SkillAuthoringSessionEntity> findByIdAndCompanyId(String id, String companyId);
}
