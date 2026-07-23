package com.codehouse.ciciassistant.openapi.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface AgentApiMemoryBindingRepository extends JpaRepository<AgentApiMemoryBindingEntity, Long> {
    Optional<AgentApiMemoryBindingEntity> findByCredentialId(Long credentialId);
    Optional<AgentApiMemoryBindingEntity> findByCredentialIdAndEnabledTrue(Long credentialId);
}
