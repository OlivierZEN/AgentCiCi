package com.codehouse.ciciassistant.customerinsight.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInsightGenerationJobRepository extends JpaRepository<CustomerInsightGenerationJobEntity, Long> {

    List<CustomerInsightGenerationJobEntity> findByProjectIdOrderByCreatedAtDesc(Long projectId);

    void deleteByProjectId(Long projectId);
}
