package com.codehouse.ciciassistant.ai.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AgentRunTraceRepository extends JpaRepository<AgentRunTraceEntity, String> {

    interface AgentRuntimeStatsProjection {
        String getAgentId();
        long getSevenDaySessionCount();
        long getSevenDayFailureCount();
        long getActiveSessionCount();
        Double getAvgLatencyMs();
        Instant getLastActiveAt();
    }

    Optional<AgentRunTraceEntity> findByTraceIdAndCompanyId(String traceId, String companyId);

    Optional<AgentRunTraceEntity> findFirstByCompanyIdAndSessionIdAndAgentIdOrderByStartedAtDesc(
            String companyId,
            String sessionId,
            String agentId);

    List<AgentRunTraceEntity> findByCompanyIdAndStartedAtBetweenOrderByStartedAtDesc(
            String companyId,
            Instant from,
            Instant to);

    List<AgentRunTraceEntity> findTop500ByCompanyIdAndStartedAtBetweenOrderByStartedAtDesc(
            String companyId,
            Instant from,
            Instant to);

    @Query("""
            select t.agentId as agentId,
                   count(t) as sevenDaySessionCount,
                   sum(case when t.status in ('FAILED', 'WAITING_CONFIRMATION') then 1 else 0 end) as sevenDayFailureCount,
                   sum(case when t.status = 'RUNNING' then 1 else 0 end) as activeSessionCount,
                   avg(t.elapsedMs) as avgLatencyMs,
                   max(t.startedAt) as lastActiveAt
            from AgentRunTraceEntity t
            where t.companyId = :companyId
              and t.startedAt between :from and :to
            group by t.agentId
            """)
    List<AgentRuntimeStatsProjection> summarizeOrgRuntime(
            @Param("companyId") String companyId,
            @Param("from") Instant from,
            @Param("to") Instant to);

    @Query("""
            select t from AgentRunTraceEntity t
            where t.companyId = :companyId
              and t.startedAt between :from and :to
              and (
                t.userId = :userId
                or t.sessionId like 'feishu:%'
                or t.sessionId like 'wechat:%'
                or t.sessionId like 'wecom-kf:%'
                or t.sessionId like 'dingtalk:%'
                or t.sessionId like 'api:%'
                or t.sessionId like 'web:%'
                or t.sessionId like 'webchat:%'
              )
            order by t.startedAt desc
            """)
    List<AgentRunTraceEntity> findVisibleRecent(
            @Param("companyId") String companyId,
            @Param("userId") String userId,
            @Param("from") Instant from,
            @Param("to") Instant to);
}
