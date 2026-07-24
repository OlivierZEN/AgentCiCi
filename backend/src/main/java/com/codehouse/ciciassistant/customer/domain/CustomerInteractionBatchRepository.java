package com.codehouse.ciciassistant.customer.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInteractionBatchRepository extends JpaRepository<CustomerInteractionBatchEntity, Long> {
    Optional<CustomerInteractionBatchEntity> findByPublicId(String publicId);
    Optional<CustomerInteractionBatchEntity> findByCompanyIdAndPublicId(String companyId, String publicId);
    List<CustomerInteractionBatchEntity> findTop20ByCompanyIdAndCrmAccountIdOrderByCreatedAtDesc(String companyId, String crmAccountId);
    List<CustomerInteractionBatchEntity> findTop100ByStatusAndUpdatedAtBeforeOrderByUpdatedAtAsc(String status, Instant updatedAt);
}
