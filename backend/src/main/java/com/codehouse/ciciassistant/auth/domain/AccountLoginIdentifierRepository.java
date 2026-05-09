package com.codehouse.ciciassistant.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountLoginIdentifierRepository extends JpaRepository<AccountLoginIdentifierEntity, String> {

    Optional<AccountLoginIdentifierEntity> findByIdentifierTypeAndNormalizedValueAndStatus(
            String identifierType,
            String normalizedValue,
            String status);

    Optional<AccountLoginIdentifierEntity> findByAccount_IdAndIdentifierTypeAndStatus(
            String accountId,
            String identifierType,
            String status);
}
