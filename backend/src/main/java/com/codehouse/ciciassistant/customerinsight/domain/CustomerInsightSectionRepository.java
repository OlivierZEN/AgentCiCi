package com.codehouse.ciciassistant.customerinsight.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInsightSectionRepository extends JpaRepository<CustomerInsightSectionEntity, Long> {

    List<CustomerInsightSectionEntity> findByProjectIdOrderByIdAsc(Long projectId);

    Optional<CustomerInsightSectionEntity> findByProjectIdAndSectionCode(Long projectId, String sectionCode);

    long countByProjectIdAndStatus(Long projectId, String status);

    void deleteByProjectId(Long projectId);
}
