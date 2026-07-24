package com.codehouse.ciciassistant.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerWorkbenchRecommendationRepository extends JpaRepository<CustomerWorkbenchRecommendationEntity, Long> {

    List<CustomerWorkbenchRecommendationEntity> findByCompanyIdAndCrmAccountIdOrderByUpdatedAtDesc(String companyId, String crmAccountId);

    List<CustomerWorkbenchRecommendationEntity> findByCompanyIdAndCrmAccountIdAndRecommendationTypeAndActionKeyOrderByUpdatedAtDesc(
            String companyId, String crmAccountId, String recommendationType, String actionKey);

    List<CustomerWorkbenchRecommendationEntity> findByCompanyIdOrderByUpdatedAtDesc(String companyId);

    Optional<CustomerWorkbenchRecommendationEntity> findByCompanyIdAndPublicId(String companyId, String publicId);

    long countByCompanyIdAndCrmAccountIdAndStatus(String companyId, String crmAccountId, String status);

    long countByCompanyId(String companyId);
}
