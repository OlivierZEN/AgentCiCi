package com.codehouse.ciciassistant.skill.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillVersionRepository extends JpaRepository<SkillVersionEntity, Long> {

    Optional<SkillVersionEntity> findTopByOrgIdAndSkillIdOrderByVersionNoDesc(String orgId, Long skillId);

    Optional<SkillVersionEntity> findByOrgIdAndSkillIdAndVersionNo(String orgId, Long skillId, Integer versionNo);

    Optional<SkillVersionEntity> findTopByOrgIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(
            String orgId, Long skillId, String publishStatus);

    List<SkillVersionEntity> findByOrgIdAndSkillIdAndRestoreVisibleTrueOrderByVersionNoDesc(String orgId, Long skillId);

    List<SkillVersionEntity> findByOrgIdAndSkillIdOrderByVersionNoDesc(String orgId, Long skillId);
}
