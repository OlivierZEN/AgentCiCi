package com.codehouse.ciciassistant.email.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAccountRepository extends JpaRepository<EmailAccountEntity, Long> {

    List<EmailAccountEntity> findByOrgIdAndUserIdOrderByIdAsc(String orgId, String userId);

    Optional<EmailAccountEntity> findByIdAndOrgIdAndUserId(Long id, String orgId, String userId);

    Optional<EmailAccountEntity> findFirstByOrgIdAndUserIdAndEnabledTrueOrderByIdAsc(String orgId, String userId);

    Optional<EmailAccountEntity> findByOrgIdAndUserIdAndEmailAddress(String orgId, String userId, String emailAddress);
}
