package com.codehouse.ciciassistant.customer.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRecommendationFeedbackRepository extends JpaRepository<CustomerRecommendationFeedbackEntity, Long> {
    Optional<CustomerRecommendationFeedbackEntity> findByOrgIdAndUserIdAndRecommendationId(
            String orgId, String userId, String recommendationId);
}
