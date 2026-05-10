package com.codehouse.ciciassistant.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserAccountRepository extends JpaRepository<UserAccountEntity, String> {

    Optional<UserAccountEntity> findByPrimaryMobile(String primaryMobile);

    Optional<UserAccountEntity> findByEmailIgnoreCase(String email);
}
