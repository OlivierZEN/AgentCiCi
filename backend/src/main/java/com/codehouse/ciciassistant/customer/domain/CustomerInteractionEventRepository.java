package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInteractionEventRepository extends JpaRepository<CustomerInteractionEventEntity, Long> {

    List<CustomerInteractionEventEntity> findByCompanyIdAndCrmAccountIdOrderByOccurredAtDesc(String companyId, String crmAccountId);

    List<CustomerInteractionEventEntity> findByCompanyIdOrderByOccurredAtDesc(String companyId);

    Optional<CustomerInteractionEventEntity> findByCompanyIdAndPublicId(String companyId, String publicId);

    long countByCompanyId(String companyId);
}
