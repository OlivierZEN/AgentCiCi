package com.codehouse.ciciassistant.skill.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillDefinitionRepository extends JpaRepository<SkillDefinitionEntity, Long> {

    Optional<SkillDefinitionEntity> findByCompanyIdAndSkillCode(String companyId, String skillCode);

    List<SkillDefinitionEntity> findByCompanyIdAndEnabledTrueOrderByBuiltinDescNameAsc(String companyId);

    List<SkillDefinitionEntity> findByCompanyIdOrderByBuiltinDescNameAsc(String companyId);

    List<SkillDefinitionEntity> findByCompanyIdAndIdInAndEnabledTrue(String companyId, List<Long> ids);

    Optional<SkillDefinitionEntity> findByIdAndCompanyId(Long id, String companyId);

    boolean existsByCompanyIdAndSkillCode(String companyId, String skillCode);

    boolean existsByCompanyIdAndSkillCodeAndIdNot(String companyId, String skillCode, Long id);
}
