package com.codehouse.ciciassistant.memory.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryVectorFragmentRepository extends JpaRepository<MemoryVectorFragmentEntity, Long> {
    Optional<MemoryVectorFragmentEntity> findByOrgIdAndMemoryRecordIdAndStatus(String orgId, Long memoryRecordId, String status);
}
