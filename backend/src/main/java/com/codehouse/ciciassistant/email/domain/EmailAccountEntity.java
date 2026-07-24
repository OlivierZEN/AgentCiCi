package com.codehouse.ciciassistant.email.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "email_account")
public class EmailAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "provider_code", nullable = false, length = 32)
    private String providerCode;

    @Column(name = "display_name", length = 128)
    private String displayName;

    @Column(name = "email_address", nullable = false, length = 256)
    private String emailAddress;

    @Column(name = "login_username", nullable = false, length = 256)
    private String loginUsername;

    @Column(name = "auth_type", nullable = false, length = 16)
    private String authType;

    @Column(name = "secret_cipher", nullable = false, columnDefinition = "TEXT")
    private String secretCipher;

    @Column(name = "secret_iv", nullable = false, length = 64)
    private String secretIv;

    @Column(name = "pop3_host", nullable = false, length = 128)
    private String pop3Host;

    @Column(name = "pop3_port", nullable = false)
    private int pop3Port;

    @Column(name = "pop3_ssl", nullable = false)
    private boolean pop3Ssl = true;

    @Column(name = "smtp_host", nullable = false, length = 128)
    private String smtpHost;

    @Column(name = "smtp_port", nullable = false)
    private int smtpPort;

    @Column(name = "smtp_ssl_mode", nullable = false, length = 16)
    private String smtpSslMode;

    @Column(name = "require_send_confirm", nullable = false)
    private boolean requireSendConfirm = true;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "last_verified_at")
    private Instant lastVerifiedAt;

    @Column(name = "last_verify_error", length = 512)
    private String lastVerifyError;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected EmailAccountEntity() {
    }

    public EmailAccountEntity(
            String companyId,
            String userId,
            String providerCode,
            String displayName,
            String emailAddress,
            String loginUsername,
            String authType,
            String secretCipher,
            String secretIv,
            String pop3Host,
            int pop3Port,
            boolean pop3Ssl,
            String smtpHost,
            int smtpPort,
            String smtpSslMode,
            boolean requireSendConfirm,
            boolean enabled) {
        this.companyId = companyId;
        this.userId = userId;
        this.providerCode = providerCode;
        this.displayName = displayName;
        this.emailAddress = emailAddress;
        this.loginUsername = loginUsername;
        this.authType = authType;
        this.secretCipher = secretCipher;
        this.secretIv = secretIv;
        this.pop3Host = pop3Host;
        this.pop3Port = pop3Port;
        this.pop3Ssl = pop3Ssl;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpSslMode = smtpSslMode;
        this.requireSendConfirm = requireSendConfirm;
        this.enabled = enabled;
    }

    public void updateProfile(String providerCode,
                              String displayName,
                              String emailAddress,
                              String loginUsername,
                              String authType,
                              String pop3Host,
                              int pop3Port,
                              boolean pop3Ssl,
                              String smtpHost,
                              int smtpPort,
                              String smtpSslMode,
                              boolean requireSendConfirm,
                              boolean enabled) {
        this.providerCode = providerCode;
        this.displayName = displayName;
        this.emailAddress = emailAddress;
        this.loginUsername = loginUsername;
        this.authType = authType;
        this.pop3Host = pop3Host;
        this.pop3Port = pop3Port;
        this.pop3Ssl = pop3Ssl;
        this.smtpHost = smtpHost;
        this.smtpPort = smtpPort;
        this.smtpSslMode = smtpSslMode;
        this.requireSendConfirm = requireSendConfirm;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void updateSecret(String secretCipher, String secretIv) {
        this.secretCipher = secretCipher;
        this.secretIv = secretIv;
        this.updatedAt = Instant.now();
    }

    public void markVerified(Instant at) {
        this.lastVerifiedAt = at;
        this.lastVerifyError = null;
        this.updatedAt = Instant.now();
    }

    public void markVerifyFailed(String error) {
        this.lastVerifyError = error == null ? null : error.substring(0, Math.min(error.length(), 512));
        this.updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getCompanyId() {
        return companyId;
    }

    public String getUserId() {
        return userId;
    }

    public String getProviderCode() {
        return providerCode;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getEmailAddress() {
        return emailAddress;
    }

    public String getLoginUsername() {
        return loginUsername;
    }

    public String getAuthType() {
        return authType;
    }

    public String getSecretCipher() {
        return secretCipher;
    }

    public String getSecretIv() {
        return secretIv;
    }

    public String getPop3Host() {
        return pop3Host;
    }

    public int getPop3Port() {
        return pop3Port;
    }

    public boolean isPop3Ssl() {
        return pop3Ssl;
    }

    public String getSmtpHost() {
        return smtpHost;
    }

    public int getSmtpPort() {
        return smtpPort;
    }

    public String getSmtpSslMode() {
        return smtpSslMode;
    }

    public boolean isRequireSendConfirm() {
        return requireSendConfirm;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public Instant getLastVerifiedAt() {
        return lastVerifiedAt;
    }

    public String getLastVerifyError() {
        return lastVerifyError;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
