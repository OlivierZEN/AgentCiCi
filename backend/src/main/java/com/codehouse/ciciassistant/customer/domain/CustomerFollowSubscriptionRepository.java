package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerFollowSubscriptionRepository extends JpaRepository<CustomerFollowSubscriptionEntity, Long> {
    Optional<CustomerFollowSubscriptionEntity> findByCompanyIdAndUserIdAndCrmAccountId(String companyId, String userId, String crmAccountId);
    List<CustomerFollowSubscriptionEntity> findByCompanyIdAndUserId(String companyId, String userId);
    void deleteByCompanyIdAndUserIdAndCrmAccountId(String companyId, String userId, String crmAccountId);
}
