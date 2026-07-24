package com.codehouse.ciciassistant.openapi.domain;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentApiFileRepository extends JpaRepository<AgentApiFileEntity, String> {

    Optional<AgentApiFileEntity> findByFileIdAndCompanyIdAndCredentialIdAndAgentId(
            String fileId,
            String companyId,
            Long credentialId,
            String agentId);

    List<AgentApiFileEntity> findByCompanyIdAndCredentialIdAndAgentIdAndFileIdIn(
            String companyId,
            Long credentialId,
            String agentId,
            Collection<String> fileIds);
}
