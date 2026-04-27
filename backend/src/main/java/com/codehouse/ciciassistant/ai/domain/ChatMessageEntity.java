package com.codehouse.ciciassistant.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_message")
public class ChatMessageEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "role_code", nullable = false, length = 16)
    private String roleCode;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessageEntity() {
    }

    public ChatMessageEntity(String sessionId, String orgId, String roleCode, String content) {
        this.sessionId = sessionId;
        this.orgId = orgId;
        this.roleCode = roleCode;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getSessionId() {
        return sessionId;
    }

    public String getOrgId() {
        return orgId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getContent() {
        return content;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
