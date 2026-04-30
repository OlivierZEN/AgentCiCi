package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSkillTemplateVersionRepository extends JpaRepository<PlatformSkillTemplateVersionEntity, Long> {

    List<PlatformSkillTemplateVersionEntity> findByOrgIdAndTemplateCodeOrderByVersionNoDesc(String orgId, String templateCode);

    Optional<PlatformSkillTemplateVersionEntity> findByOrgIdAndTemplateCodeAndVersionNo(
            String orgId, String templateCode, Integer versionNo);

    Optional<PlatformSkillTemplateVersionEntity> findTopByOrgIdAndTemplateCodeOrderByVersionNoDesc(String orgId, String templateCode);
}
