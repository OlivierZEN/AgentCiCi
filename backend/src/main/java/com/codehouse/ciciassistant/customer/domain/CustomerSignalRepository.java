package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerSignalRepository extends JpaRepository<CustomerSignalEntity, Long> {
    List<CustomerSignalEntity> findByOrgIdAndCrmAccountIdOrderByUpdatedAtDesc(String orgId, String crmAccountId);
    Optional<CustomerSignalEntity> findByOrgIdAndPublicId(String orgId, String publicId);
    long countByOrgIdAndCrmAccountIdAndStatus(String orgId, String crmAccountId, String status);
}
