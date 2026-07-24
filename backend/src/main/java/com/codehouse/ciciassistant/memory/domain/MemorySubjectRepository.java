package com.codehouse.ciciassistant.memory.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemorySubjectRepository extends JpaRepository<MemorySubjectEntity, Long> {

    Optional<MemorySubjectEntity> findByCompanyIdAndApplicationCodeAndSubjectTypeAndExternalRef(
            String companyId, String applicationCode, String subjectType, String externalRef);
}
