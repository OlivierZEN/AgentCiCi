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
@Table(name = "account_auth_credential")
public class AccountAuthCredentialEntity {

    public static final String TYPE_PASSWORD = "PASSWORD";
    public static final String STATUS_ACTIVE = "ACTIVE";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccountEntity account;

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

    protected AccountAuthCredentialEntity() {
    }

    public AccountAuthCredentialEntity(UserAccountEntity account) {
        Instant now = Instant.now();
        this.id = UUID.randomUUID().toString();
        this.account = account;
        this.credentialType = TYPE_PASSWORD;
        this.status = STATUS_ACTIVE;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public UserAccountEntity getAccount() {
        return account;
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

    public void replacePassword(String passwordHash, String salt, int iterations, String algorithm) {
        this.passwordHash = passwordHash;
        this.salt = salt;
        this.iterations = iterations;
        this.algorithm = algorithm;
        this.credentialType = TYPE_PASSWORD;
        this.status = STATUS_ACTIVE;
        this.updatedAt = Instant.now();
    }
}
