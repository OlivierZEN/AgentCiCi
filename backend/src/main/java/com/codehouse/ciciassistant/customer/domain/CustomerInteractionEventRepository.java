package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInteractionEventRepository extends JpaRepository<CustomerInteractionEventEntity, Long> {

    List<CustomerInteractionEventEntity> findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc(String orgId, String crmAccountId);

    long countByOrgId(String orgId);
}
