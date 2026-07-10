package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCrmWriteAuditRepository extends JpaRepository<CustomerCrmWriteAuditEntity, Long> {
    Optional<CustomerCrmWriteAuditEntity> findByOrgIdAndUserIdAndIdempotencyKey(String orgId, String userId, String idempotencyKey);
    List<CustomerCrmWriteAuditEntity> findByOrgIdAndRecommendationIdOrderByCreatedAtDesc(String orgId, String recommendationId);
    List<CustomerCrmWriteAuditEntity> findByOrgIdAndUserIdOrderByCreatedAtDesc(String orgId, String userId);
}
