package com.codehouse.ciciassistant.skill.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillApiToolRepository extends JpaRepository<SkillApiToolEntity, Long> {

    void deleteByCompanyIdAndSkillVersionId(String companyId, Long skillVersionId);

    List<SkillApiToolEntity> findByCompanyIdAndSkillVersionIdInAndEnabledTrueOrderByIdAsc(
            String companyId, Collection<Long> skillVersionIds);

    Optional<SkillApiToolEntity> findByCompanyIdAndToolNameAndEnabledTrue(String companyId, String toolName);
}
