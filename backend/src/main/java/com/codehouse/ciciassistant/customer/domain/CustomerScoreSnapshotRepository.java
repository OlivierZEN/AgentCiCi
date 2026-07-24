package com.codehouse.ciciassistant.customer.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerScoreSnapshotRepository extends JpaRepository<CustomerScoreSnapshotEntity, Long> {
    Optional<CustomerScoreSnapshotEntity> findByCompanyIdAndCrmAccountId(String companyId, String crmAccountId);
    List<CustomerScoreSnapshotEntity> findByCompanyIdAndCrmAccountIdIn(String companyId, Collection<String> crmAccountIds);
}
