package com.codehouse.ciciassistant.openapi.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiCredentialRepository extends JpaRepository<AgentApiCredentialEntity, Long> {

    List<AgentApiCredentialEntity> findByCompanyIdAndAgentIdOrderByCreatedAtDesc(String companyId, String agentId);

    Optional<AgentApiCredentialEntity> findByIdAndCompanyIdAndAgentId(Long id, String companyId, String agentId);

    Optional<AgentApiCredentialEntity> findByPublicId(String publicId);

    boolean existsByPublicId(String publicId);
}
