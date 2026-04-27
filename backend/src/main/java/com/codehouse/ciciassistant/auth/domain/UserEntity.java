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
@Table(name = "app_user")
public class UserEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private OrgEntity org;

    @Column(name = "mobile", nullable = false, length = 32)
    private String mobile;

    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    @Column(name = "nickname", length = 128)
    private String nickname;

    @Column(name = "cc_username", length = 128)
    private String ccUsername;

    @Column(name = "cc_safetymark", length = 128)
    private String ccSafetymark;

    @Column(name = "avatar_base64", columnDefinition = "TEXT")
    private String avatarBase64;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected UserEntity() {
    }

    public UserEntity(OrgEntity org, String mobile, String roleCode) {
        this.id = UUID.randomUUID().toString();
        this.org = org;
        this.mobile = mobile;
        this.roleCode = roleCode;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public OrgEntity getOrg() {
        return org;
    }

    public String getMobile() {
        return mobile;
    }

    public void setMobile(String mobile) {
        this.mobile = mobile;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getCcUsername() {
        return ccUsername;
    }

    public void setCcUsername(String ccUsername) {
        this.ccUsername = ccUsername;
    }

    public String getCcSafetymark() {
        return ccSafetymark;
    }

    public void setCcSafetymark(String ccSafetymark) {
        this.ccSafetymark = ccSafetymark;
    }

    public String getAvatarBase64() {
        return avatarBase64;
    }

    public void setAvatarBase64(String avatarBase64) {
        this.avatarBase64 = avatarBase64;
    }
}
