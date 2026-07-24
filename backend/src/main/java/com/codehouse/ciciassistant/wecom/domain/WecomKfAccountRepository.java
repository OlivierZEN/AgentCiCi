package com.codehouse.ciciassistant.wecom.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WecomKfAccountRepository extends JpaRepository<WecomKfAccountEntity, Long> {

    List<WecomKfAccountEntity> findByEnabledTrue();

    List<WecomKfAccountEntity> findByCompanyIdOrderByUpdatedAtDescIdDesc(String companyId);

    Optional<WecomKfAccountEntity> findByIdAndCompanyId(Long id, String companyId);

    Optional<WecomKfAccountEntity> findByCompanyIdAndOpenKfId(String companyId, String openKfId);

    Optional<WecomKfAccountEntity> findByCompanyIdAndOpenKfIdAndEnabledTrue(String companyId, String openKfId);
}
