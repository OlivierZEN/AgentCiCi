package com.codehouse.ciciassistant.billing.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BillingSubscriptionRepository extends JpaRepository<BillingSubscriptionEntity, Long> {

    Optional<BillingSubscriptionEntity> findByOrgId(String orgId);
}
