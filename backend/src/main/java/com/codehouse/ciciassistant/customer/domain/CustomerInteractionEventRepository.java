package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInteractionEventRepository extends JpaRepository<CustomerInteractionEventEntity, Long> {

    List<CustomerInteractionEventEntity> findByOrgIdAndCrmAccountIdOrderByOccurredAtDesc(String orgId, String crmAccountId);

    List<CustomerInteractionEventEntity> findByOrgIdOrderByOccurredAtDesc(String orgId);

    Optional<CustomerInteractionEventEntity> findByOrgIdAndPublicId(String orgId, String publicId);

    long countByOrgId(String orgId);
}
