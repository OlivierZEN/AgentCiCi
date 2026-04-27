package com.codehouse.ciciassistant.integration.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationAppRepository extends JpaRepository<IntegrationAppEntity, Long> {

    List<IntegrationAppEntity> findByOrgIdOrderByIdAsc(String orgId);

    Optional<IntegrationAppEntity> findByOrgIdAndAppCode(String orgId, String appCode);

    List<IntegrationAppEntity> findByAppCodeAndEnabledTrueOrderByIdAsc(String appCode);
}
