package com.codehouse.ciciassistant.wecom.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WecomKfAccountRepository extends JpaRepository<WecomKfAccountEntity, Long> {

    List<WecomKfAccountEntity> findByEnabledTrue();

    List<WecomKfAccountEntity> findByOrgIdOrderByUpdatedAtDescIdDesc(String orgId);

    Optional<WecomKfAccountEntity> findByIdAndOrgId(Long id, String orgId);

    Optional<WecomKfAccountEntity> findByOrgIdAndOpenKfId(String orgId, String openKfId);

    Optional<WecomKfAccountEntity> findByOrgIdAndOpenKfIdAndEnabledTrue(String orgId, String openKfId);
}
