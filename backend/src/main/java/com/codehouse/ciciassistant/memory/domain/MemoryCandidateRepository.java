package com.codehouse.ciciassistant.memory.domain;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
public interface MemoryCandidateRepository extends JpaRepository<MemoryCandidateEntity, Long> {
    Optional<MemoryCandidateEntity> findByIdAndCompanyId(Long id, String companyId);
    Optional<MemoryCandidateEntity> findByIdAndCompanyIdAndAgentId(Long id, String companyId, String agentId);
    java.util.List<MemoryCandidateEntity> findByCompanyIdAndAgentIdOrderByUpdatedAtDesc(String companyId, String agentId);
    java.util.List<MemoryCandidateEntity> findByCompanyIdAndSubjectId(String companyId, Long subjectId);
}
