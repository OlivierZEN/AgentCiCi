package com.codehouse.ciciassistant.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunTraceRepository extends JpaRepository<AgentRunTraceEntity, String> {

    Optional<AgentRunTraceEntity> findByTraceIdAndOrgId(String traceId, String orgId);

    @Query("""
            select t from AgentRunTraceEntity t
            where t.orgId = :orgId
              and t.startedAt between :from and :to
              and (
                t.userId = :userId
                or t.sessionId like 'feishu:%'
                or t.sessionId like 'wechat:%'
                or t.sessionId like 'dingtalk:%'
                or t.sessionId like 'web:%'
                or t.sessionId like 'webchat:%'
              )
            order by t.startedAt desc
            """)
    List<AgentRunTraceEntity> findVisibleRecent(
            @Param("orgId") String orgId,
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
