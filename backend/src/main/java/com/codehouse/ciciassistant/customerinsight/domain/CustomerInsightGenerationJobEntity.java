package com.codehouse.ciciassistant.customerinsight.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_insight_generation_job")
public class CustomerInsightGenerationJobEntity {

    public static final String STATUS_RUNNING = "RUNNING";
    public static final String STATUS_SUCCESS = "SUCCESS";
    public static final String STATUS_FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_id", nullable = false)
    private Long projectId;

    @Column(name = "section_code", length = 64)
    private String sectionCode;

    @Column(name = "job_type", nullable = false, length = 32)
    private String jobType;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "request_summary", nullable = false, columnDefinition = "TEXT")
    private String requestSummary;

    @Column(name = "result_summary", columnDefinition = "TEXT")
    private String resultSummary;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected CustomerInsightGenerationJobEntity() {
    }

    public CustomerInsightGenerationJobEntity(Long projectId, String sectionCode, String jobType, String requestSummary) {
        this.projectId = projectId;
        this.sectionCode = sectionCode;
        this.jobType = jobType;
        this.status = STATUS_RUNNING;
        this.requestSummary = requestSummary == null || requestSummary.isBlank() ? "customer insight generation" : requestSummary;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public Long getProjectId() { return projectId; }
    public String getSectionCode() { return sectionCode; }
    public String getJobType() { return jobType; }
    public String getStatus() { return status; }
    public String getRequestSummary() { return requestSummary; }
    public String getResultSummary() { return resultSummary; }
    public String getTraceId() { return traceId; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getCompletedAt() { return completedAt; }

    public void markSuccess(String resultSummary, String traceId) {
        this.status = STATUS_SUCCESS;
        this.resultSummary = resultSummary;
        this.traceId = traceId;
        this.completedAt = Instant.now();
    }

    public void markFailed(String resultSummary, String traceId) {
        this.status = STATUS_FAILED;
        this.resultSummary = resultSummary;
        this.traceId = traceId;
        this.completedAt = Instant.now();
    }
}
