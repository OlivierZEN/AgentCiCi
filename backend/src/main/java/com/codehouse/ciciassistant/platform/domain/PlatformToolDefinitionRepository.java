package com.codehouse.ciciassistant.platform.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformToolDefinitionRepository extends JpaRepository<PlatformToolDefinitionEntity, Long> {

    List<PlatformToolDefinitionEntity> findByCompanyIdOrderByCategoryAscDisplayNameAsc(String companyId);

    Optional<PlatformToolDefinitionEntity> findByCompanyIdAndToolName(String companyId, String toolName);
}
