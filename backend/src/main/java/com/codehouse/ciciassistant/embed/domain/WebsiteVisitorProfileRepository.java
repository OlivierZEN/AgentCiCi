package com.codehouse.ciciassistant.embed.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsiteVisitorProfileRepository extends JpaRepository<WebsiteVisitorProfileEntity, String> {

    Optional<WebsiteVisitorProfileEntity> findByCompanyIdAndAgentIdAndExternalTenantIdAndExternalUserId(
            String companyId, String agentId, String externalTenantId, String externalUserId);
}
