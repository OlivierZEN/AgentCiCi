package com.codehouse.ciciassistant.tool.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolDefinitionRepository extends JpaRepository<ToolDefinitionEntity, Long> {

    List<ToolDefinitionEntity> findByCompanyIdAndEnabledTrue(String companyId);

    java.util.Optional<ToolDefinitionEntity> findByCompanyIdAndToolName(String companyId, String toolName);
}
