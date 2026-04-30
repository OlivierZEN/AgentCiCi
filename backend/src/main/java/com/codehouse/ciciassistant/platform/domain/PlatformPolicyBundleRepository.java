package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformPolicyBundleRepository extends JpaRepository<PlatformPolicyBundleEntity, Long> {

    List<PlatformPolicyBundleEntity> findByOrgIdAndBundleCodeOrderByVersionNoDesc(String orgId, String bundleCode);

    Optional<PlatformPolicyBundleEntity> findByOrgIdAndBundleCodeAndVersionNo(String orgId, String bundleCode, Integer versionNo);

    Optional<PlatformPolicyBundleEntity> findTopByOrgIdAndBundleCodeAndPublishStatusOrderByVersionNoDesc(
            String orgId,
            String bundleCode,
            String publishStatus
    );
}
