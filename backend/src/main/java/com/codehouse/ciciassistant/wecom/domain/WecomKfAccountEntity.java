package com.codehouse.ciciassistant.wecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "wecom_kf_account")
public class WecomKfAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "corp_id", nullable = false, length = 64)
    private String corpId;

    @Column(name = "open_kfid", nullable = false, length = 128)
    private String openKfId;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    @Column(name = "secret_cipher", nullable = false, columnDefinition = "TEXT")
    private String secretCipher;

    @Column(name = "secret_iv", nullable = false, length = 64)
    private String secretIv;

    @Column(name = "token", nullable = false, length = 128)
    private String token;

    @Column(name = "encoding_aes_key_cipher", nullable = false, columnDefinition = "TEXT")
    private String encodingAesKeyCipher;

    @Column(name = "encoding_aes_key_iv", nullable = false, length = 64)
    private String encodingAesKeyIv;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "run_as_user_id", nullable = false, length = 64)
    private String runAsUserId;

    @Column(name = "mobile_entry_id", nullable = false, unique = true)
    private UUID mobileEntryId = UUID.randomUUID();

    @Column(name = "wecom_app_agent_id", length = 64)
    private String wecomAppAgentId;

    @Column(name = "wecom_app_secret_cipher", columnDefinition = "TEXT")
    private String wecomAppSecretCipher;

    @Column(name = "wecom_app_secret_iv", length = 64)
    private String wecomAppSecretIv;

    @Column(name = "wecom_app_access_token_cipher", columnDefinition = "TEXT")
    private String wecomAppAccessTokenCipher;

    @Column(name = "wecom_app_access_token_iv", length = 64)
    private String wecomAppAccessTokenIv;

    @Column(name = "wecom_app_access_token_expires_at")
    private Instant wecomAppAccessTokenExpiresAt;

    @Column(name = "mobile_handoff_enabled", nullable = false)
    private boolean mobileHandoffEnabled = false;

    @Column(name = "enabled", nullable = false)
    private boolean enabled = true;

    @Column(name = "sync_cursor", columnDefinition = "TEXT")
    private String syncCursor;

    @Column(name = "access_token_cipher", columnDefinition = "TEXT")
    private String accessTokenCipher;

    @Column(name = "access_token_iv", length = 64)
    private String accessTokenIv;

    @Column(name = "access_token_expires_at")
    private Instant accessTokenExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt = Instant.now();

    protected WecomKfAccountEntity() {
    }

    public WecomKfAccountEntity(String companyId,
                                String corpId,
                                String openKfId,
                                String name,
                                String secretCipher,
                                String secretIv,
                                String token,
                                String encodingAesKeyCipher,
                                String encodingAesKeyIv,
                                String agentId,
                                String runAsUserId) {
        this.companyId = companyId;
        this.corpId = corpId;
        this.openKfId = openKfId;
        this.name = name;
        this.secretCipher = secretCipher;
        this.secretIv = secretIv;
        this.token = token;
        this.encodingAesKeyCipher = encodingAesKeyCipher;
        this.encodingAesKeyIv = encodingAesKeyIv;
        this.agentId = agentId;
        this.runAsUserId = runAsUserId;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getCorpId() { return corpId; }
    public String getOpenKfId() { return openKfId; }
    public String getName() { return name; }
    public String getSecretCipher() { return secretCipher; }
    public String getSecretIv() { return secretIv; }
    public String getToken() { return token; }
    public String getEncodingAesKeyCipher() { return encodingAesKeyCipher; }
    public String getEncodingAesKeyIv() { return encodingAesKeyIv; }
    public String getAgentId() { return agentId; }
    public String getRunAsUserId() { return runAsUserId; }
    public UUID getMobileEntryId() { return mobileEntryId; }
    public String getWecomAppAgentId() { return wecomAppAgentId; }
    public String getWecomAppSecretCipher() { return wecomAppSecretCipher; }
    public String getWecomAppSecretIv() { return wecomAppSecretIv; }
    public String getWecomAppAccessTokenCipher() { return wecomAppAccessTokenCipher; }
    public String getWecomAppAccessTokenIv() { return wecomAppAccessTokenIv; }
    public Instant getWecomAppAccessTokenExpiresAt() { return wecomAppAccessTokenExpiresAt; }
    public boolean isMobileHandoffEnabled() { return mobileHandoffEnabled; }
    public boolean isEnabled() { return enabled; }
    public String getSyncCursor() { return syncCursor; }
    public String getAccessTokenCipher() { return accessTokenCipher; }
    public String getAccessTokenIv() { return accessTokenIv; }
    public Instant getAccessTokenExpiresAt() { return accessTokenExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void updateSyncCursor(String syncCursor) {
        this.syncCursor = syncCursor;
        this.updatedAt = Instant.now();
    }

    public void updateAccessToken(String cipher, String iv, Instant expiresAt) {
        this.accessTokenCipher = cipher;
        this.accessTokenIv = iv;
        this.accessTokenExpiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public void updateProfile(String corpId,
                              String openKfId,
                              String name,
                              String token,
                              String agentId,
                              String runAsUserId,
                              String wecomAppAgentId,
                              boolean mobileHandoffEnabled,
                              boolean enabled) {
        this.corpId = corpId;
        this.openKfId = openKfId;
        this.name = name;
        this.token = token;
        this.agentId = agentId;
        this.runAsUserId = runAsUserId;
        this.wecomAppAgentId = blank(wecomAppAgentId);
        this.mobileHandoffEnabled = mobileHandoffEnabled;
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    public void updateSecret(String cipher, String iv) {
        this.secretCipher = cipher;
        this.secretIv = iv;
        clearAccessToken();
        this.updatedAt = Instant.now();
    }

    public void updateEncodingAesKey(String cipher, String iv) {
        this.encodingAesKeyCipher = cipher;
        this.encodingAesKeyIv = iv;
        this.updatedAt = Instant.now();
    }

    public void updateWecomAppSecret(String cipher, String iv) {
        this.wecomAppSecretCipher = cipher;
        this.wecomAppSecretIv = iv;
        clearWecomAppAccessToken();
        this.updatedAt = Instant.now();
    }

    public void updateWecomAppAccessToken(String cipher, String iv, Instant expiresAt) {
        this.wecomAppAccessTokenCipher = cipher;
        this.wecomAppAccessTokenIv = iv;
        this.wecomAppAccessTokenExpiresAt = expiresAt;
        this.updatedAt = Instant.now();
    }

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    private void clearAccessToken() {
        this.accessTokenCipher = null;
        this.accessTokenIv = null;
        this.accessTokenExpiresAt = null;
    }

    private void clearWecomAppAccessToken() {
        this.wecomAppAccessTokenCipher = null;
        this.wecomAppAccessTokenIv = null;
        this.wecomAppAccessTokenExpiresAt = null;
    }

    private String blank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
