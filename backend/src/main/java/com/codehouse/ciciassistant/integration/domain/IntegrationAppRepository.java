package com.codehouse.ciciassistant.integration.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface IntegrationAppRepository extends JpaRepository<IntegrationAppEntity, Long> {

    List<IntegrationAppEntity> findByCompanyIdOrderByIdAsc(String companyId);

    Optional<IntegrationAppEntity> findByCompanyIdAndAppCode(String companyId, String appCode);

    List<IntegrationAppEntity> findByAppCodeAndEnabledTrueOrderByIdAsc(String appCode);
}
