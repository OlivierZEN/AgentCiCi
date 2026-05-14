package com.codehouse.ciciassistant.embed.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrgEmbedAppConfigRepository extends JpaRepository<OrgEmbedAppConfigEntity, Long> {

    Optional<OrgEmbedAppConfigEntity> findByOrgIdAndAppCode(String orgId, String appCode);

    List<OrgEmbedAppConfigEntity> findByOrgId(String orgId);
}
