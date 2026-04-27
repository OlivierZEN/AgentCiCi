package com.codehouse.ciciassistant.userworkflow.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAgentProfileRepository extends JpaRepository<UserAgentProfileEntity, Long> {

    Optional<UserAgentProfileEntity> findByOrgIdAndUserIdAndAgentId(String orgId, String userId, String agentId);
}
