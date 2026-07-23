package com.codehouse.ciciassistant.auth.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SematticeProvisioningBindingRepository extends JpaRepository<SematticeProvisioningBindingEntity, String> {

    Optional<SematticeProvisioningBindingEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<SematticeProvisioningBindingEntity> findByOrgId(String orgId);
}
