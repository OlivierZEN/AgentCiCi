package com.codehouse.ciciassistant.wecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "wecom_kf_account")
public class WecomKfAccountEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

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

    public WecomKfAccountEntity(String orgId,
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
        this.orgId = orgId;
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
    public String getOrgId() { return orgId; }
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
                              boolean enabled) {
        this.corpId = corpId;
        this.openKfId = openKfId;
        this.name = name;
        this.token = token;
        this.agentId = agentId;
        this.runAsUserId = runAsUserId;
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

    public void updateEnabled(boolean enabled) {
        this.enabled = enabled;
        this.updatedAt = Instant.now();
    }

    private void clearAccessToken() {
        this.accessTokenCipher = null;
        this.accessTokenIv = null;
        this.accessTokenExpiresAt = null;
    }
}
