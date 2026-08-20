package com.codehouse.ciciassistant.wecom.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface WecomKfMessageRepository extends JpaRepository<WecomKfMessageEntity, Long> {

    boolean existsByCompanyIdAndMsgId(String companyId, String msgId);

    Optional<WecomKfMessageEntity> findFirstByCompanyIdAndOpenKfIdAndExternalUserIdAndOriginOrderByCreatedAtDesc(
            String companyId, String openKfId, String externalUserId, Integer origin);

    @Query(value = """
            SELECT DISTINCT ON (external_userid)
                   external_userid AS "externalUserId",
                   content_summary AS "contentSummary"
              FROM wecom_kf_message
             WHERE company_id = :companyId
               AND open_kfid = :openKfId
               AND origin = 3
               AND external_userid IS NOT NULL
             ORDER BY external_userid, created_at DESC
            """, nativeQuery = true)
    List<LatestCustomerSummary> findLatestCustomerSummaries(@Param("companyId") String companyId,
                                                             @Param("openKfId") String openKfId);

    interface LatestCustomerSummary {
        String getExternalUserId();
        String getContentSummary();
    }
}
