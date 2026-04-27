package com.codehouse.ciciassistant.skill.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SkillVersionRepository extends JpaRepository<SkillVersionEntity, Long> {

    Optional<SkillVersionEntity> findTopByOrgIdAndSkillIdOrderByVersionNoDesc(String orgId, Long skillId);

    Optional<SkillVersionEntity> findTopByOrgIdAndSkillIdAndPublishStatusOrderByVersionNoDesc(
            String orgId, Long skillId, String publishStatus);
}
