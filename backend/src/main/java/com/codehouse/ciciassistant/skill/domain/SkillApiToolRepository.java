package com.codehouse.ciciassistant.skill.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillApiToolRepository extends JpaRepository<SkillApiToolEntity, Long> {

    void deleteByOrgIdAndSkillVersionId(String orgId, Long skillVersionId);

    List<SkillApiToolEntity> findByOrgIdAndSkillVersionIdInAndEnabledTrueOrderByIdAsc(
            String orgId, Collection<Long> skillVersionIds);

    Optional<SkillApiToolEntity> findByOrgIdAndToolNameAndEnabledTrue(String orgId, String toolName);
}
