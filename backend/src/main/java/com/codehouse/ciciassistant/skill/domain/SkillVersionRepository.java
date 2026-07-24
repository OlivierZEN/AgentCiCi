package com.codehouse.ciciassistant.skill.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillVersionRepository extends JpaRepository<SkillVersionEntity, Long> {

    Optional<SkillVersionEntity> findByIdAndCompanyId(Long id, String companyId);

    List<SkillVersionEntity> findByCompanyIdAndIdIn(String companyId, List<Long> ids);

    Optional<SkillVersionEntity> findTopByCompanyIdAndSkillIdOrderByVersionNoDesc(String companyId, Long skillId);

    Optional<SkillVersionEntity> findByCompanyIdAndSkillIdAndVersionNo(String companyId, Long skillId, Integer versionNo);

    Optional<SkillVersionEntity> findTopByCompanyIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(
            String companyId, Long skillId, String publishStatus);

    List<SkillVersionEntity> findByCompanyIdAndSkillIdAndRestoreVisibleTrueOrderByVersionNoDesc(String companyId, Long skillId);

    List<SkillVersionEntity> findByCompanyIdAndSkillIdOrderByVersionNoDesc(String companyId, Long skillId);
}
