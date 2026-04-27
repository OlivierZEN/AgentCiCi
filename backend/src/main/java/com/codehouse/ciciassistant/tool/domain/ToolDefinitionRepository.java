package com.codehouse.ciciassistant.tool.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ToolDefinitionRepository extends JpaRepository<ToolDefinitionEntity, Long> {

    List<ToolDefinitionEntity> findByOrgIdAndEnabledTrue(String orgId);

    java.util.Optional<ToolDefinitionEntity> findByOrgIdAndToolName(String orgId, String toolName);
}
