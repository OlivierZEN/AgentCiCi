package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSkillTemplateVersionRepository extends JpaRepository<PlatformSkillTemplateVersionEntity, Long> {

    List<PlatformSkillTemplateVersionEntity> findByCompanyIdAndTemplateCodeOrderByVersionNoDesc(String companyId, String templateCode);

    Optional<PlatformSkillTemplateVersionEntity> findByCompanyIdAndTemplateCodeAndVersionNo(
            String companyId, String templateCode, Integer versionNo);

    Optional<PlatformSkillTemplateVersionEntity> findTopByCompanyIdAndTemplateCodeOrderByVersionNoDesc(String companyId, String templateCode);
}
