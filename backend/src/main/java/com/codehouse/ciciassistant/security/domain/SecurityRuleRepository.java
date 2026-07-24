package com.codehouse.ciciassistant.security.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SecurityRuleRepository extends JpaRepository<SecurityRuleEntity, Long> {

    List<SecurityRuleEntity> findByCompanyIdOrderByUpdatedAtDescIdDesc(String companyId);

    List<SecurityRuleEntity> findByCompanyIdAndEnabledTrueOrderByUpdatedAtDescIdDesc(String companyId);

    Optional<SecurityRuleEntity> findByIdAndCompanyId(Long id, String companyId);

    List<SecurityRuleEntity> findByCompanyIdAndCategoryOrderByUpdatedAtDescIdDesc(
            String companyId, String category, Pageable pageable);
}
