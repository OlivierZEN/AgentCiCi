package com.codehouse.ciciassistant.company;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "company_profile")
public class CompanyProfileEntity {

    @Id
    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "short_name", length = 64)
    private String shortName;

    @Column(name = "contact_name", length = 128)
    private String contactName;

    @Column(name = "contact_phone", length = 64)
    private String contactPhone;

    @Column(name = "contact_email", length = 256)
    private String contactEmail;

    @Column(name = "website", length = 256)
    private String website;

    @Column(name = "industry", length = 128)
    private String industry;

    @Column(name = "company_size", length = 64)
    private String companySize;

    @Column(name = "timezone", length = 64)
    private String timezone;

    @Column(name = "notes", columnDefinition = "TEXT")
    private String notes;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "updated_by", length = 64)
    private String updatedBy;

    protected CompanyProfileEntity() {
    }

    public CompanyProfileEntity(String companyId, String updatedBy) {
        Instant now = Instant.now();
        this.companyId = companyId;
        this.timezone = "Asia/Shanghai";
        this.createdAt = now;
        this.updatedAt = now;
        this.updatedBy = normalize(updatedBy, 64);
    }

    public void update(AdminCompanyProfileService.ProfileUpdateCommand command, String actorId) {
        this.shortName = normalize(command.shortName(), 64);
        this.contactName = normalize(command.contactName(), 128);
        this.contactPhone = normalize(command.contactPhone(), 64);
        this.contactEmail = normalize(command.contactEmail(), 256);
        this.website = normalize(command.website(), 256);
        this.industry = normalize(command.industry(), 128);
        this.companySize = normalize(command.companySize(), 64);
        this.timezone = normalize(command.timezone(), 64);
        this.notes = normalize(command.notes(), 4000);
        this.updatedBy = normalize(actorId, 64);
        this.updatedAt = Instant.now();
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getShortName() {
        return shortName;
    }

    public String getContactName() {
        return contactName;
    }

    public String getContactPhone() {
        return contactPhone;
    }

    public String getContactEmail() {
        return contactEmail;
    }

    public String getWebsite() {
        return website;
    }

    public String getIndustry() {
        return industry;
    }

    public String getCompanySize() {
        return companySize;
    }

    public String getTimezone() {
        return timezone;
    }

    public String getNotes() {
        return notes;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public String getUpdatedBy() {
        return updatedBy;
    }

    private static String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength);
    }
}
