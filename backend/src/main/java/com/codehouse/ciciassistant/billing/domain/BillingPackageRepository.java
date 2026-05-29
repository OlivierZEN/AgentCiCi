package com.codehouse.ciciassistant.billing.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingPackageRepository extends JpaRepository<BillingPackageEntity, Long> {

    Optional<BillingPackageEntity> findByPackageCode(String packageCode);

    boolean existsByPackageCode(String packageCode);

    List<BillingPackageEntity> findByDeploymentModeOrderByPackageTypeAscSortOrderAscPackageCodeAsc(String deploymentMode);

    List<BillingPackageEntity> findByDeploymentModeAndPackageTypeOrderBySortOrderAscPackageCodeAsc(String deploymentMode,
                                                                                                    String packageType);

    List<BillingPackageEntity> findAllByOrderByDeploymentModeAscPackageTypeAscSortOrderAscPackageCodeAsc();
}
