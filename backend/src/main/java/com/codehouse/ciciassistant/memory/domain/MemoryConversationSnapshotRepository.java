package com.codehouse.ciciassistant.memory.domain;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MemoryConversationSnapshotRepository extends JpaRepository<MemoryConversationSnapshotEntity, Long> {

    Optional<MemoryConversationSnapshotEntity> findByOrgIdAndApplicationCodeAndConversationRef(
            String orgId, String applicationCode, String conversationRef);

    long deleteByOrgIdAndSubjectId(String orgId, Long subjectId);
}
