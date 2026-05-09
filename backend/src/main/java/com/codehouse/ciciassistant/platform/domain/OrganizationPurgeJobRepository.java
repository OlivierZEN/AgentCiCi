package com.codehouse.ciciassistant.platform.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface OrganizationPurgeJobRepository extends JpaRepository<OrganizationPurgeJobEntity, Long> {

    Optional<OrganizationPurgeJobEntity> findTopByOrgIdOrderByCreatedAtDesc(String orgId);

    List<OrganizationPurgeJobEntity> findTop20ByOrgIdOrderByCreatedAtDesc(String orgId);

    Optional<OrganizationPurgeJobEntity> findByIdAndOrgId(Long id, String orgId);

    Optional<OrganizationPurgeJobEntity> findByIdAndOrgIdAndDryRunTrueAndStatus(Long id, String orgId, String status);

    List<OrganizationPurgeJobEntity> findTop5ByStatusAndDryRunFalseOrderByCreatedAtAsc(String status);

    List<OrganizationPurgeJobEntity> findTop10ByStatusAndDryRunFalseAndLockExpiresAtBeforeOrderByLockExpiresAtAsc(
            String status, Instant lockExpiresAt);

    boolean existsByOrgIdAndDryRunFalseAndStatusIn(String orgId, List<String> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE OrganizationPurgeJobEntity job
            SET job.status = :runningStatus,
                job.workerId = :workerId,
                job.lockedAt = :lockedAt,
                job.lockExpiresAt = :lockExpiresAt,
                job.startedAt = :lockedAt,
                job.finishedAt = null,
                job.errorMessage = null,
                job.attemptCount = job.attemptCount + 1,
                job.updatedAt = :lockedAt
            WHERE job.id = :id
              AND job.status = :queuedStatus
              AND job.dryRun = false
            """)
    int claimQueuedJob(@Param("id") Long id,
                       @Param("queuedStatus") String queuedStatus,
                       @Param("runningStatus") String runningStatus,
                       @Param("workerId") String workerId,
                       @Param("lockedAt") Instant lockedAt,
                       @Param("lockExpiresAt") Instant lockExpiresAt);
}
