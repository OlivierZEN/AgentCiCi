package com.codehouse.ciciassistant.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAccountCredentialRepository extends JpaRepository<PlatformAccountCredentialEntity, String> {

    Optional<PlatformAccountCredentialEntity> findByPlatformAccount_IdAndCredentialTypeAndStatus(
            String platformAccountId,
            String credentialType,
            String status);
}
