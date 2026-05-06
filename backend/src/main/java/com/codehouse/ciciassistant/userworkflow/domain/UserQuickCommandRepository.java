package com.codehouse.ciciassistant.userworkflow.domain;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserQuickCommandRepository extends JpaRepository<UserQuickCommandEntity, Long> {

    List<UserQuickCommandEntity> findByOrgIdAndUserIdAndAgentIdAndEnabledTrueOrderBySortOrderAscIdAsc(
            String orgId,
            String userId,
            String agentId);

    @Query("""
            select coalesce(max(item.sortOrder), 0)
            from UserQuickCommandEntity item
            where item.orgId = :orgId
              and item.userId = :userId
              and item.agentId = :agentId
            """)
    int maxSortOrder(@Param("orgId") String orgId,
                     @Param("userId") String userId,
                     @Param("agentId") String agentId);
}
