package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerDynamicSignalRepository extends JpaRepository<CustomerDynamicSignalEntity, Long> {
    List<CustomerDynamicSignalEntity> findByCompanyIdAndCrmAccountIdOrderByOccurredAtDesc(String companyId, String crmAccountId);
    List<CustomerDynamicSignalEntity> findByCompanyIdAndSourceEventId(String companyId, String sourceEventId);
    Optional<CustomerDynamicSignalEntity> findByCompanyIdAndPublicId(String companyId, String publicId);
}
