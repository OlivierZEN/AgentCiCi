package com.codehouse.ciciassistant.security.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityRuleRepository extends JpaRepository<SecurityRuleEntity, Long> {

    List<SecurityRuleEntity> findByOrgIdOrderByUpdatedAtDescIdDesc(String orgId);

    List<SecurityRuleEntity> findByOrgIdAndEnabledTrueOrderByUpdatedAtDescIdDesc(String orgId);

    Optional<SecurityRuleEntity> findByIdAndOrgId(Long id, String orgId);

    List<SecurityRuleEntity> findByOrgIdAndCategoryOrderByUpdatedAtDescIdDesc(
            String orgId, String category, Pageable pageable);
}
