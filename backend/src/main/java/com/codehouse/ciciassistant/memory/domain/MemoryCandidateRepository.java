package com.codehouse.ciciassistant.memory.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryCandidateRepository extends JpaRepository<MemoryCandidateEntity, Long> {
    Optional<MemoryCandidateEntity> findByIdAndOrgId(Long id, String orgId);
    java.util.List<MemoryCandidateEntity> findByOrgIdAndSubjectId(String orgId, Long subjectId);
}
