package com.codehouse.ciciassistant.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "account_login_identifier")
public class AccountLoginIdentifierEntity {

    public static final String TYPE_MOBILE = "MOBILE";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccountEntity account;

    @Column(name = "identifier_type", nullable = false, length = 32)
    private String identifierType;

    @Column(name = "normalized_value", nullable = false, length = 256)
    private String normalizedValue;

    @Column(name = "display_value", nullable = false, length = 256)
    private String displayValue;

    @Column(name = "is_primary", nullable = false)
    private boolean primary;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "verified_at")
    private Instant verifiedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected AccountLoginIdentifierEntity() {
    }

    public AccountLoginIdentifierEntity(UserAccountEntity account, String identifierType, String normalizedValue, String displayValue) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.account = account;
        this.identifierType = identifierType;
        this.normalizedValue = normalizedValue;
        this.displayValue = displayValue;
        this.primary = true;
        this.status = STATUS_ACTIVE;
        this.verifiedAt = now;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getId() {
        return id;
    }

    public UserAccountEntity getAccount() {
        return account;
    }

    public String getIdentifierType() {
        return identifierType;
    }

    public String getNormalizedValue() {
        return normalizedValue;
    }

    public String getDisplayValue() {
        return displayValue;
    }

    public boolean isPrimary() {
        return primary;
    }

    public String getStatus() {
        return status;
    }

    public void updateMobileValue(String normalizedValue, String displayValue) {
        this.normalizedValue = normalizedValue;
        this.displayValue = displayValue;
        this.primary = true;
        this.status = STATUS_ACTIVE;
        this.verifiedAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
