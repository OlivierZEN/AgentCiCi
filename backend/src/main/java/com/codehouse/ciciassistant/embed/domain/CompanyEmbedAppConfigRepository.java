package com.codehouse.ciciassistant.embed.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CompanyEmbedAppConfigRepository extends JpaRepository<CompanyEmbedAppConfigEntity, Long> {

    Optional<CompanyEmbedAppConfigEntity> findByCompanyIdAndAppCode(String companyId, String appCode);

    List<CompanyEmbedAppConfigEntity> findByCompanyId(String companyId);
}
