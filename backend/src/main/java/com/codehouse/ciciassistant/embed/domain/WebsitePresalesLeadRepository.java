package com.codehouse.ciciassistant.embed.domain;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WebsitePresalesLeadRepository extends JpaRepository<WebsitePresalesLeadEntity, String> {

    boolean existsByCompanyIdAndAgentIdAndProfileIdAndContactHash(
            String companyId, String agentId, String profileId, String contactHash);
}
