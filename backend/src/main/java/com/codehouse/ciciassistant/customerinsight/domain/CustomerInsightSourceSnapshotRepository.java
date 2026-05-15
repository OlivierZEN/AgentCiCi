package com.codehouse.ciciassistant.customerinsight.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInsightSourceSnapshotRepository extends JpaRepository<CustomerInsightSourceSnapshotEntity, Long> {

    List<CustomerInsightSourceSnapshotEntity> findByProjectIdOrderByCollectedAtDesc(Long projectId);

    void deleteByProjectId(Long projectId);
}
