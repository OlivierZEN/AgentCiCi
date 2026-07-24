package com.codehouse.ciciassistant.model.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelProviderConfigRepository extends JpaRepository<ModelProviderConfigEntity, Long> {

    Optional<ModelProviderConfigEntity> findByCompanyIdAndProviderCode(String companyId, String providerCode);

    List<ModelProviderConfigEntity> findByCompanyIdOrderByIdAsc(String companyId);
}
