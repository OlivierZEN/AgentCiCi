package com.codehouse.ciciassistant.embed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "website_presales_lead")
public class WebsitePresalesLeadEntity {

    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "profile_id", nullable = false, length = 64)
    private String profileId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "chat_session_id", nullable = false, length = 64)
    private String chatSessionId;

    @Column(name = "contact_type", nullable = false, length = 16)
    private String contactType;

    @Column(name = "contact_cipher", nullable = false, columnDefinition = "TEXT")
    private String contactCipher;

    @Column(name = "contact_iv", nullable = false, length = 64)
    private String contactIv;

    @Column(name = "contact_hash", nullable = false, length = 64)
    private String contactHash;

    @Column(name = "need_summary", columnDefinition = "TEXT")
    private String needSummary;

    @Column(name = "source", nullable = false, length = 32)
    private String source;

    @Column(name = "consented_at", nullable = false)
    private Instant consentedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected WebsitePresalesLeadEntity() {
    }

    public WebsitePresalesLeadEntity(String profileId,
                                     String companyId,
                                     String agentId,
                                     String chatSessionId,
                                     String contactType,
                                     String contactCipher,
                                     String contactIv,
                                     String contactHash,
                                     String needSummary) {
        this.id = UUID.randomUUID().toString();
        this.profileId = profileId;
        this.companyId = companyId;
        this.agentId = agentId;
        this.chatSessionId = chatSessionId;
        this.contactType = contactType;
        this.contactCipher = contactCipher;
        this.contactIv = contactIv;
        this.contactHash = contactHash;
        this.needSummary = needSummary;
        this.source = "website";
        this.consentedAt = Instant.now();
        this.createdAt = this.consentedAt;
    }
}
