package com.codehouse.ciciassistant.security.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityDetectionEventRepository extends JpaRepository<SecurityDetectionEventEntity, Long> {

    List<SecurityDetectionEventEntity> findByCompanyIdOrderByCreatedAtDescIdDesc(String companyId, Pageable pageable);

    List<SecurityDetectionEventEntity> findByCompanyIdAndReviewedOrderByCreatedAtDescIdDesc(
            String companyId, boolean reviewed, Pageable pageable);

    long countByCompanyId(String companyId);

    long countByCompanyIdAndAction(String companyId, String action);

    long countByCompanyIdAndReviewedFalse(String companyId);

    Optional<SecurityDetectionEventEntity> findByIdAndCompanyId(Long id, String companyId);
}
