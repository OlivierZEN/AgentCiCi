package com.codehouse.ciciassistant.mcp.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {

    List<McpServerEntity> findByOrgIdAndEnabledTrue(String orgId);

    List<McpServerEntity> findByOrgIdOrderByIdDesc(String orgId);

    Optional<McpServerEntity> findByIdAndOrgId(Long id, String orgId);
}
