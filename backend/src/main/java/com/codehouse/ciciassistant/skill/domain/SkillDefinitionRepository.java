package com.codehouse.ciciassistant.skill.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillDefinitionRepository extends JpaRepository<SkillDefinitionEntity, Long> {

    Optional<SkillDefinitionEntity> findByOrgIdAndSkillCode(String orgId, String skillCode);

    List<SkillDefinitionEntity> findByOrgIdAndEnabledTrueOrderByBuiltinDescNameAsc(String orgId);

    List<SkillDefinitionEntity> findByOrgIdOrderByBuiltinDescNameAsc(String orgId);

    List<SkillDefinitionEntity> findByOrgIdAndIdInAndEnabledTrue(String orgId, List<Long> ids);

    Optional<SkillDefinitionEntity> findByIdAndOrgId(Long id, String orgId);

    boolean existsByOrgIdAndSkillCode(String orgId, String skillCode);

    boolean existsByOrgIdAndSkillCodeAndIdNot(String orgId, String skillCode, Long id);
}
