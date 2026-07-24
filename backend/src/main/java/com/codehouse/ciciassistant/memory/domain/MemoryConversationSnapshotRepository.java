package com.codehouse.ciciassistant.memory.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryConversationSnapshotRepository extends JpaRepository<MemoryConversationSnapshotEntity, Long> {

    Optional<MemoryConversationSnapshotEntity> findByCompanyIdAndApplicationCodeAndConversationRef(
            String companyId, String applicationCode, String conversationRef);

    long deleteByCompanyIdAndSubjectId(String companyId, Long subjectId);
}
