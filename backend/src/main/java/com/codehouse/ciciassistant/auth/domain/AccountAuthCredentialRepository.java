package com.codehouse.ciciassistant.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountAuthCredentialRepository extends JpaRepository<AccountAuthCredentialEntity, String> {

    Optional<AccountAuthCredentialEntity> findByAccount_IdAndCredentialTypeAndStatus(
            String accountId,
            String credentialType,
            String status);
}
