package com.codehouse.ciciassistant.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "security_detection_event")
public class SecurityDetectionEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "surface", nullable = false, length = 64)
    private String surface;

    @Column(name = "action", nullable = false, length = 16)
    private String action;

    @Column(name = "severity", nullable = false, length = 16)
    private String severity;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "rule_name", nullable = false, length = 128)
    private String ruleName;

    @Column(name = "matched_summary", nullable = false, length = 500)
    private String matchedSummary;

    @Column(name = "redacted_text", nullable = false, columnDefinition = "TEXT")
    private String redactedText;

    @Column(name = "policy_version", nullable = false, length = 64)
    private String policyVersion;

    @Column(name = "reviewed", nullable = false)
    private boolean reviewed;

    @Column(name = "review_result", nullable = false, length = 32)
    private String reviewResult;

    @Column(name = "review_note", nullable = false, length = 500)
    private String reviewNote;

    @Column(name = "reviewed_by", nullable = false, length = 64)
    private String reviewedBy;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected SecurityDetectionEventEntity() {
    }

    public SecurityDetectionEventEntity(String orgId,
                                        String userId,
                                        String surface,
                                        String action,
                                        String severity,
                                        String category,
                                        String ruleName,
                                        String matchedSummary,
                                        String redactedText,
                                        String policyVersion) {
        this.orgId = orgId;
        this.userId = userId == null || userId.isBlank() ? "system" : userId;
        this.surface = surface;
        this.action = action;
        this.severity = severity;
        this.category = category;
        this.ruleName = ruleName == null ? "" : ruleName;
        this.matchedSummary = matchedSummary == null ? "" : matchedSummary;
        this.redactedText = redactedText == null ? "" : redactedText;
        this.policyVersion = policyVersion == null ? "builtin-v1" : policyVersion;
        this.reviewed = false;
        this.reviewResult = "";
        this.reviewNote = "";
        this.reviewedBy = "";
        this.createdAt = Instant.now();
    }

    public void review(String result, String note, String reviewedBy) {
        this.reviewed = true;
        this.reviewResult = result == null ? "" : result;
        this.reviewNote = note == null ? "" : note;
        this.reviewedBy = reviewedBy == null ? "" : reviewedBy;
        this.reviewedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getUserId() {
        return userId;
    }

    public String getSurface() {
        return surface;
    }

    public String getAction() {
        return action;
    }

    public String getSeverity() {
        return severity;
    }

    public String getCategory() {
        return category;
    }

    public String getRuleName() {
        return ruleName;
    }

    public String getMatchedSummary() {
        return matchedSummary;
    }

    public String getRedactedText() {
        return redactedText;
    }

    public String getPolicyVersion() {
        return policyVersion;
    }

    public boolean isReviewed() {
        return reviewed;
    }

    public String getReviewResult() {
        return reviewResult;
    }

    public String getReviewNote() {
        return reviewNote;
    }

    public String getReviewedBy() {
        return reviewedBy;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
