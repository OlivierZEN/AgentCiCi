package com.codehouse.ciciassistant.platform.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CompanyPurgeJobRepository extends JpaRepository<CompanyPurgeJobEntity, Long> {

    Optional<CompanyPurgeJobEntity> findTopByCompanyIdOrderByCreatedAtDesc(String companyId);

    List<CompanyPurgeJobEntity> findTop20ByCompanyIdOrderByCreatedAtDesc(String companyId);

    Optional<CompanyPurgeJobEntity> findByIdAndCompanyId(Long id, String companyId);

    Optional<CompanyPurgeJobEntity> findByIdAndCompanyIdAndDryRunTrueAndStatus(Long id, String companyId, String status);

    List<CompanyPurgeJobEntity> findTop5ByStatusAndDryRunFalseOrderByCreatedAtAsc(String status);

    List<CompanyPurgeJobEntity> findTop10ByStatusAndDryRunFalseAndLockExpiresAtBeforeOrderByLockExpiresAtAsc(
            String status, Instant lockExpiresAt);

    boolean existsByCompanyIdAndDryRunFalseAndStatusIn(String companyId, List<String> statuses);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE CompanyPurgeJobEntity job
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
