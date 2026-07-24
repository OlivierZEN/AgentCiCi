package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformPolicyBundleRepository extends JpaRepository<PlatformPolicyBundleEntity, Long> {

    List<PlatformPolicyBundleEntity> findByCompanyIdAndBundleCodeOrderByVersionNoDesc(String companyId, String bundleCode);

    Optional<PlatformPolicyBundleEntity> findByCompanyIdAndBundleCodeAndVersionNo(String companyId, String bundleCode, Integer versionNo);

    Optional<PlatformPolicyBundleEntity> findTopByCompanyIdAndBundleCodeAndPublishStatusOrderByVersionNoDesc(
            String companyId,
            String bundleCode,
            String publishStatus
    );
}
