package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerFollowSubscriptionRepository extends JpaRepository<CustomerFollowSubscriptionEntity, Long> {
    Optional<CustomerFollowSubscriptionEntity> findByOrgIdAndUserIdAndCrmAccountId(String orgId, String userId, String crmAccountId);
    List<CustomerFollowSubscriptionEntity> findByOrgIdAndUserId(String orgId, String userId);
    void deleteByOrgIdAndUserIdAndCrmAccountId(String orgId, String userId, String crmAccountId);
}
