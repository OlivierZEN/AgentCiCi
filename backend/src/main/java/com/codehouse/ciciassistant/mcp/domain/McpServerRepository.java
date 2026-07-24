package com.codehouse.ciciassistant.mcp.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface McpServerRepository extends JpaRepository<McpServerEntity, Long> {

    List<McpServerEntity> findByCompanyIdAndEnabledTrue(String companyId);

    List<McpServerEntity> findByCompanyIdOrderByIdDesc(String companyId);

    Optional<McpServerEntity> findByIdAndCompanyId(Long id, String companyId);
}
