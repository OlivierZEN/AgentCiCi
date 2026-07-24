package com.codehouse.ciciassistant.customer.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerRecommendationFeedbackRepository extends JpaRepository<CustomerRecommendationFeedbackEntity, Long> {
    Optional<CustomerRecommendationFeedbackEntity> findByCompanyIdAndUserIdAndRecommendationId(
            String companyId, String userId, String recommendationId);
}
