package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInteractionAssetRepository extends JpaRepository<CustomerInteractionAssetEntity, Long> {
    List<CustomerInteractionAssetEntity> findByBatchIdOrderBySortOrderAsc(Long batchId);
    Optional<CustomerInteractionAssetEntity> findByOrgIdAndPublicId(String orgId, String publicId);
    boolean existsByOrgIdAndSha256(String orgId, String sha256);
}
