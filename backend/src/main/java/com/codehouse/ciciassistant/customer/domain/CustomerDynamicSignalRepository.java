package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerDynamicSignalRepository extends JpaRepository<CustomerDynamicSignalEntity, Long> {
    List<CustomerDynamicSignalEntity> findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc(String orgId, String crmAccountId);
    List<CustomerDynamicSignalEntity> findByOrgIdAndSourceEventId(String orgId, String sourceEventId);
    Optional<CustomerDynamicSignalEntity> findByOrgIdAndPublicId(String orgId, String publicId);
}
