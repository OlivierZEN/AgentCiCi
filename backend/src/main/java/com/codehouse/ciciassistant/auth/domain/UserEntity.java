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
@Table(name = "organization_member")
public class UserEntity {

    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "org_id", nullable = false)
    private OrgEntity org;

    @ManyToOne(optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private UserAccountEntity account;

    @Column(name = "role_code", nullable = false, length = 32)
    private String roleCode;

    @Column(name = "member_status", nullable = false, length = 32)
    private String memberStatus;

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

    public UserEntity(OrgEntity org, UserAccountEntity account, String roleCode) {
        this.id = UUID.randomUUID().toString();
        this.org = org;
        this.account = account;
        this.roleCode = roleCode;
        this.memberStatus = STATUS_ACTIVE;
        this.createdAt = Instant.now();
    }

    public String getId() {
        return id;
    }

    public OrgEntity getOrg() {
        return org;
    }

    public UserAccountEntity getAccount() {
        return account;
    }

    public String getAccountId() {
        return account == null ? "" : account.getId();
    }

    public String getMobile() {
        return account == null ? "" : account.getPrimaryMobile();
    }

    public String getRoleCode() {
        return roleCode;
    }

    public void setRoleCode(String roleCode) {
        this.roleCode = roleCode;
    }

    public String getMemberStatus() {
        return memberStatus;
    }

    public void setMemberStatus(String memberStatus) {
        this.memberStatus = memberStatus;
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
