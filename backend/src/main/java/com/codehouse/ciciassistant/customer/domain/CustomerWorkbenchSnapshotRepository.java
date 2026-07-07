package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerWorkbenchSnapshotRepository extends JpaRepository<CustomerWorkbenchSnapshotEntity, Long> {

    List<CustomerWorkbenchSnapshotEntity> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<CustomerWorkbenchSnapshotEntity> findByOrgIdAndCrmAccountId(String orgId, String crmAccountId);

    long countByOrgId(String orgId);
}
