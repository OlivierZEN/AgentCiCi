package com.codehouse.ciciassistant.email.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EmailAccountRepository extends JpaRepository<EmailAccountEntity, Long> {

    List<EmailAccountEntity> findByCompanyIdAndUserIdOrderByIdAsc(String companyId, String userId);

    Optional<EmailAccountEntity> findByIdAndCompanyIdAndUserId(Long id, String companyId, String userId);

    Optional<EmailAccountEntity> findFirstByCompanyIdAndUserIdAndEnabledTrueOrderByIdAsc(String companyId, String userId);

    Optional<EmailAccountEntity> findByCompanyIdAndUserIdAndEmailAddress(String companyId, String userId, String emailAddress);
}
