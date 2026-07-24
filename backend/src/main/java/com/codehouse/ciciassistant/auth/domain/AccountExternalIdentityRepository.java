package com.codehouse.ciciassistant.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountExternalIdentityRepository extends JpaRepository<AccountExternalIdentityEntity, String> {

    Optional<AccountExternalIdentityEntity> findByIssuerAndSubject(String issuer, String subject);

    Optional<AccountExternalIdentityEntity> findByAccount_Id(String accountId);
}
