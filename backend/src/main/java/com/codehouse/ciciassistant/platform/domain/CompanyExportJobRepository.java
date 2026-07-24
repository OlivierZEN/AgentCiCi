package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyExportJobRepository extends JpaRepository<CompanyExportJobEntity, Long> {

    List<CompanyExportJobEntity> findTop20ByCompanyIdOrderByCreatedAtDesc(String companyId);

    List<CompanyExportJobEntity> findAllByCompanyId(String companyId);

    Optional<CompanyExportJobEntity> findByIdAndCompanyId(Long id, String companyId);
}
