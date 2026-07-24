package com.codehouse.ciciassistant.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "security_review_item")
public class SecurityReviewItemEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "assignee", nullable = false, length = 64)
    private String assignee;

    @Column(name = "note", nullable = false, length = 500)
    private String note;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SecurityReviewItemEntity() {
    }

    public SecurityReviewItemEntity(String companyId, Long eventId, String status, String assignee, String note) {
        this.companyId = companyId;
        this.eventId = eventId;
        this.status = status;
        this.assignee = assignee == null ? "" : assignee;
        this.note = note == null ? "" : note;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() {
        this.updatedAt = Instant.now();
    }
}
