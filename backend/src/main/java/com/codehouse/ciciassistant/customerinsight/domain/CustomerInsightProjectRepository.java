package com.codehouse.ciciassistant.customerinsight.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInsightProjectRepository extends JpaRepository<CustomerInsightProjectEntity, Long> {

    List<CustomerInsightProjectEntity> findByOrgIdOrderByUpdatedAtDesc(String orgId);

    Optional<CustomerInsightProjectEntity> findByOrgIdAndPublicId(String orgId, String publicId);
}
