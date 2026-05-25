package com.codehouse.ciciassistant.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAccountRepository extends JpaRepository<PlatformAccountEntity, String> {

    Optional<PlatformAccountEntity> findByEmailIgnoreCase(String email);

    Optional<PlatformAccountEntity> findByMobile(String mobile);
}
