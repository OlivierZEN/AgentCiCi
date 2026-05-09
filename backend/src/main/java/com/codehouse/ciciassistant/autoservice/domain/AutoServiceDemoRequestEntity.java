package com.codehouse.ciciassistant.autoservice.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "autoservice_demo_request")
public class AutoServiceDemoRequestEntity {

    public static final String STATUS_NEW = "NEW";
    public static final String STATUS_CONTACTED = "CONTACTED";
    public static final String STATUS_CLOSED = "CLOSED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "site", nullable = false, length = 32)
    private String site;

    @Column(name = "locale", nullable = false, length = 16)
    private String locale;

    @Column(name = "company_name", nullable = false, length = 128)
    private String companyName;

    @Column(name = "contact_name", nullable = false, length = 64)
    private String contactName;

    @Column(name = "mobile", nullable = false, length = 64)
    private String mobile;

    @Column(name = "email", length = 128)
    private String email;

    @Column(name = "role_title", length = 128)
    private String roleTitle;

    @Column(name = "scenario", columnDefinition = "TEXT")
    private String scenario;

    @Column(name = "source_path", length = 256)
    private String sourcePath;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "handled_by", length = 64)
    private String handledBy;

    @Column(name = "handled_note", columnDefinition = "TEXT")
    private String handledNote;

    @Column(name = "handled_at")
    private Instant handledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AutoServiceDemoRequestEntity() {
    }

    public AutoServiceDemoRequestEntity(String site,
                                        String locale,
                                        String companyName,
                                        String contactName,
                                        String mobile,
                                        String email,
                                        String roleTitle,
                                        String scenario,
                                        String sourcePath) {
        Instant now = Instant.now();
        this.site = site;
        this.locale = locale;
        this.companyName = companyName;
        this.contactName = contactName;
        this.mobile = mobile;
        this.email = email;
        this.roleTitle = roleTitle;
        this.scenario = scenario;
        this.sourcePath = sourcePath;
        this.status = STATUS_NEW;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getSite() {
        return site;
    }

    public String getLocale() {
        return locale;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getContactName() {
        return contactName;
    }

    public String getMobile() {
        return mobile;
    }

    public String getEmail() {
        return email;
    }

    public String getRoleTitle() {
        return roleTitle;
    }

    public String getScenario() {
        return scenario;
    }

    public String getSourcePath() {
        return sourcePath;
    }

    public String getStatus() {
        return status;
    }

    public String getHandledBy() {
        return handledBy;
    }

    public String getHandledNote() {
        return handledNote;
    }

    public Instant getHandledAt() {
        return handledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void updateStatus(String status, String handledBy, String handledNote) {
        this.status = status;
        this.handledBy = handledBy;
        this.handledNote = handledNote;
        this.handledAt = STATUS_NEW.equals(status) ? null : Instant.now();
        this.updatedAt = Instant.now();
    }
}
