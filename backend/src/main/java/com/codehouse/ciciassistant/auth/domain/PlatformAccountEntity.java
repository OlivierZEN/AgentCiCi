package com.codehouse.ciciassistant.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "platform_account")
public class PlatformAccountEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "email", nullable = false, length = 128, unique = true)
    private String email;

    @Column(name = "mobile", nullable = false, length = 32, unique = true)
    private String mobile;

    @Column(name = "display_name", nullable = false, length = 128)
    private String displayName;

    @Column(name = "roles_json", nullable = false, columnDefinition = "TEXT")
    private String rolesJson;

    @Column(name = "theme_code", nullable = false, length = 32)
    private String themeCode = "gilded";

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformAccountEntity() {
    }

    public PlatformAccountEntity(String email, String mobile, String displayName, String rolesJson) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.email = email;
        this.mobile = mobile;
        this.displayName = displayName;
        this.rolesJson = rolesJson;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getMobile() {
        return mobile;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRolesJson() {
        return rolesJson;
    }

    public String getThemeCode() {
        return themeCode;
    }

    public String getStatus() {
        return status;
    }

    public void setThemeCode(String themeCode) {
        this.themeCode = themeCode;
        this.updatedAt = Instant.now();
    }

    public void updateBootstrapFields(String email, String mobile, String displayName, String rolesJson) {
        this.email = email;
        this.mobile = mobile;
        this.displayName = displayName;
        this.rolesJson = rolesJson;
        this.status = STATUS_ACTIVE;
        this.updatedAt = Instant.now();
    }
}
