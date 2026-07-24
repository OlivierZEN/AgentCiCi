package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerCrmWriteAuditRepository extends JpaRepository<CustomerCrmWriteAuditEntity, Long> {
    Optional<CustomerCrmWriteAuditEntity> findByCompanyIdAndUserIdAndIdempotencyKey(String companyId, String userId, String idempotencyKey);
    List<CustomerCrmWriteAuditEntity> findByCompanyIdAndRecommendationIdOrderByCreatedAtDesc(String companyId, String recommendationId);
    List<CustomerCrmWriteAuditEntity> findByCompanyIdAndUserIdOrderByCreatedAtDesc(String companyId, String userId);
}
