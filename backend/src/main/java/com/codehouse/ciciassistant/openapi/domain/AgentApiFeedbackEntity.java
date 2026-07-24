package com.codehouse.ciciassistant.openapi.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "agent_api_feedback")
public class AgentApiFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 64)
    private String messageId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "credential_id", nullable = false)
    private Long credentialId;

    @Column(name = "agent_id", nullable = false, length = 64)
    private String agentId;

    @Column(name = "rating", nullable = false, length = 32)
    private String rating;

    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected AgentApiFeedbackEntity() {
    }

    public AgentApiFeedbackEntity(String messageId,
                                  String companyId,
                                  Long credentialId,
                                  String agentId,
                                  String rating,
                                  String content) {
        this.messageId = messageId;
        this.companyId = companyId;
        this.credentialId = credentialId;
        this.agentId = agentId;
        this.rating = rating;
        this.content = content;
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getMessageId() { return messageId; }
    public String getRating() { return rating; }
    public String getContent() { return content; }
    public Instant getCreatedAt() { return createdAt; }
}
