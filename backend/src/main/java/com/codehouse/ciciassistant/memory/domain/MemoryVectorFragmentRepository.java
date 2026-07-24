package com.codehouse.ciciassistant.memory.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryVectorFragmentRepository extends JpaRepository<MemoryVectorFragmentEntity, Long> {
    Optional<MemoryVectorFragmentEntity> findByCompanyIdAndMemoryRecordIdAndStatus(String companyId, Long memoryRecordId, String status);
}
