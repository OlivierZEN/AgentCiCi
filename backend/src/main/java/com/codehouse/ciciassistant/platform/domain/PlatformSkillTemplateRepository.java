package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformSkillTemplateRepository extends JpaRepository<PlatformSkillTemplateEntity, Long> {

    Optional<PlatformSkillTemplateEntity> findByCompanyIdAndTemplateCode(String companyId, String templateCode);

    List<PlatformSkillTemplateEntity> findByCompanyIdOrderByNameAsc(String companyId);
}
