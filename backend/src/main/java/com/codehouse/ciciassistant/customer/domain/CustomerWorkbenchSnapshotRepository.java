package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerWorkbenchSnapshotRepository extends JpaRepository<CustomerWorkbenchSnapshotEntity, Long> {

    List<CustomerWorkbenchSnapshotEntity> findByCompanyIdOrderByUpdatedAtDesc(String companyId);

    Optional<CustomerWorkbenchSnapshotEntity> findByCompanyIdAndCrmAccountId(String companyId, String crmAccountId);

    long countByCompanyId(String companyId);
}
