package com.codehouse.ciciassistant.wecom.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WecomKfAccountRepository extends JpaRepository<WecomKfAccountEntity, Long> {

    List<WecomKfAccountEntity> findByEnabledTrue();

    Optional<WecomKfAccountEntity> findByOrgIdAndOpenKfIdAndEnabledTrue(String orgId, String openKfId);
}
