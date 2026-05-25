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
@Table(name = "platform_account_credential")
public class PlatformAccountCredentialEntity {

    public static final String TYPE_PASSWORD = "PASSWORD";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "platform_account_id", nullable = false)
    private PlatformAccountEntity platformAccount;

    @Column(name = "credential_type", nullable = false, length = 32)
    private String credentialType;

    @Column(name = "password_hash", nullable = false, length = 256)
    private String passwordHash;

    @Column(name = "salt", nullable = false, length = 128)
    private String salt;

    @Column(name = "iterations", nullable = false)
    private int iterations;

    @Column(name = "algorithm", nullable = false, length = 64)
    private String algorithm;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PlatformAccountCredentialEntity() {
    }

    public PlatformAccountCredentialEntity(PlatformAccountEntity platformAccount) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.platformAccount = platformAccount;
        this.credentialType = TYPE_PASSWORD;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getSalt() {
        return salt;
    }

    public int getIterations() {
        return iterations;
    }

    public String getAlgorithm() {
        return algorithm;
    }
}
