package com.codehouse.ciciassistant.model.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ModelProviderConfigRepository extends JpaRepository<ModelProviderConfigEntity, Long> {

    Optional<ModelProviderConfigEntity> findByOrgIdAndProviderCode(String orgId, String providerCode);

    List<ModelProviderConfigEntity> findByOrgIdOrderByIdAsc(String orgId);
}
