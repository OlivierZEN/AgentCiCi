package com.codehouse.ciciassistant.security.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityDetectionEventRepository extends JpaRepository<SecurityDetectionEventEntity, Long> {

    List<SecurityDetectionEventEntity> findByOrgIdOrderByCreatedAtDescIdDesc(String orgId, Pageable pageable);

    List<SecurityDetectionEventEntity> findByOrgIdAndReviewedOrderByCreatedAtDescIdDesc(
            String orgId, boolean reviewed, Pageable pageable);

    long countByOrgId(String orgId);

    long countByOrgIdAndAction(String orgId, String action);

    long countByOrgIdAndReviewedFalse(String orgId);

    Optional<SecurityDetectionEventEntity> findByIdAndOrgId(Long id, String orgId);
}
