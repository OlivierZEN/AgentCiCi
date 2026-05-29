package com.codehouse.ciciassistant.billing.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingEditionRepository extends JpaRepository<BillingEditionEntity, Long> {

    Optional<BillingEditionEntity> findByEditionCode(String editionCode);

    boolean existsByEditionCode(String editionCode);

    Optional<BillingEditionEntity> findFirstByDeploymentModeAndEnabledTrueOrderBySortOrderAscEditionCodeAsc(String deploymentMode);

    List<BillingEditionEntity> findByDeploymentModeOrderBySortOrderAscEditionCodeAsc(String deploymentMode);

    List<BillingEditionEntity> findAllByOrderByDeploymentModeAscSortOrderAscEditionCodeAsc();
}
