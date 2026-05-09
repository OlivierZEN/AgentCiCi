package com.codehouse.ciciassistant.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_account")
public class UserAccountEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "primary_mobile", nullable = false, length = 32, unique = true)
    private String primaryMobile;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserAccountEntity() {
    }

    public UserAccountEntity(String primaryMobile) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.primaryMobile = primaryMobile;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getPrimaryMobile() {
        return primaryMobile;
    }

    public void setPrimaryMobile(String primaryMobile) {
        this.primaryMobile = primaryMobile;
        touch();
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
        touch();
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    private void touch() {
        this.updatedAt = Instant.now();
    }
}
