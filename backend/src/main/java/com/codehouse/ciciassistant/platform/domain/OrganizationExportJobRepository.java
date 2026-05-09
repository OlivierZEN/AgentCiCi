package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrganizationExportJobRepository extends JpaRepository<OrganizationExportJobEntity, Long> {

    List<OrganizationExportJobEntity> findTop20ByOrgIdOrderByCreatedAtDesc(String orgId);

    List<OrganizationExportJobEntity> findAllByOrgId(String orgId);

    Optional<OrganizationExportJobEntity> findByIdAndOrgId(Long id, String orgId);
}
