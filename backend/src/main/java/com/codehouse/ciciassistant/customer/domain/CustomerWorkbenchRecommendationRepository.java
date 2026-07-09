package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerWorkbenchRecommendationRepository extends JpaRepository<CustomerWorkbenchRecommendationEntity, Long> {

    List<CustomerWorkbenchRecommendationEntity> findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(String orgId, String crmAccountId);

    List<CustomerWorkbenchRecommendationEntity> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<CustomerWorkbenchRecommendationEntity> findByOrgIdAndPublicId(String orgId, String publicId);

    long countByOrgIdAndCrmAccountIdAndStatus(String orgId, String crmAccountId, String status);

    long countByOrgId(String orgId);
}
