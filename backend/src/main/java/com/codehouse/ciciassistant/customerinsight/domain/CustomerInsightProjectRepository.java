package com.codehouse.ciciassistant.customerinsight.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CustomerInsightProjectRepository extends JpaRepository<CustomerInsightProjectEntity, Long> {

    List<CustomerInsightProjectEntity> findByCompanyIdOrderByUpdatedAtDesc(String companyId);

    Optional<CustomerInsightProjectEntity> findByCompanyIdAndPublicId(String companyId, String publicId);
}
