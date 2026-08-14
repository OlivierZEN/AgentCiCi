package com.codehouse.ciciassistant.ai.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ChatAttachmentRepository extends JpaRepository<ChatAttachmentEntity, Long> {

    Optional<ChatAttachmentEntity> findByCompanyIdAndUserIdAndSessionIdAndPublicId(
            String companyId, String userId, String sessionId, String publicId);

    Optional<ChatAttachmentEntity> findByCompanyIdAndUserIdAndSessionIdAndClientAttachmentId(
            String companyId, String userId, String sessionId, String clientAttachmentId);

    List<ChatAttachmentEntity> findByCompanyIdAndUserIdAndSessionIdOrderBySlotNoAsc(
            String companyId, String userId, String sessionId);

    List<ChatAttachmentEntity> findByCompanyIdAndUserIdAndSessionIdAndPublicIdIn(
            String companyId, String userId, String sessionId, List<String> publicIds);

    List<ChatAttachmentEntity> findByMessageIdOrderBySlotNoAsc(Long messageId);

    @Query("select a.slotNo from ChatAttachmentEntity a where a.companyId = :companyId and a.sessionId = :sessionId")
    List<Integer> findUsedSlots(@Param("companyId") String companyId, @Param("sessionId") String sessionId);
}
