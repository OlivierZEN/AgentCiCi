package com.codehouse.ciciassistant.embed.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "website_visit_session")
public class WebsiteVisitSessionEntity {

    public static final String AWAITING_CHOICE = "AWAITING_CHOICE";
    public static final String ACTIVE = "ACTIVE";
    public static final String CONTACT_REQUESTED = "CONTACT_REQUESTED";
    public static final String COMPLETED = "COMPLETED";
    public static final String SERVICE_REDIRECTED = "SERVICE_REDIRECTED";

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

    @Column(name = "external_visit_id", length = 64)
    private String externalVisitId;

    @Column(name = "status", nullable = false, length = 32)
    private String status;

    @Column(name = "resume_choice", length = 24)
    private String resumeChoice;

    @Column(name = "inherited_summary", columnDefinition = "TEXT")
    private String inheritedSummary;

    @Column(name = "current_summary", columnDefinition = "TEXT")
    private String currentSummary;

    @Column(name = "turn_count", nullable = false)
    private int turnCount;

    @Column(name = "last_intent", length = 32)
    private String lastIntent;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    protected WebsiteVisitSessionEntity() {
    }

    public WebsiteVisitSessionEntity(String profileId,
                                     String companyId,
                                     String agentId,
                                     String chatSessionId,
                                     String externalVisitId,
                                     String inheritedSummary,
                                     boolean choiceRequired) {
        this.id = UUID.randomUUID().toString();
        this.profileId = profileId;
        this.companyId = companyId;
        this.agentId = agentId;
        this.chatSessionId = chatSessionId;
        this.externalVisitId = externalVisitId == null || externalVisitId.isBlank() ? null : externalVisitId.trim();
        this.inheritedSummary = inheritedSummary == null || inheritedSummary.isBlank() ? null : inheritedSummary.trim();
        this.status = choiceRequired ? AWAITING_CHOICE : ACTIVE;
        this.turnCount = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getId() { return id; }
    public String getProfileId() { return profileId; }
    public String getCompanyId() { return companyId; }
    public String getAgentId() { return agentId; }
    public String getChatSessionId() { return chatSessionId; }
    public String getExternalVisitId() { return externalVisitId; }
    public String getStatus() { return status; }
    public String getResumeChoice() { return resumeChoice; }
    public String getInheritedSummary() { return inheritedSummary; }
    public String getCurrentSummary() { return currentSummary; }
    public int getTurnCount() { return turnCount; }
    public String getLastIntent() { return lastIntent; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void choose(String choice) {
        this.resumeChoice = choice;
        if ("START_NEW".equals(choice)) {
            this.inheritedSummary = null;
        }
        this.status = ACTIVE;
        this.updatedAt = Instant.now();
    }

    public int nextTurn(String intent) {
        this.turnCount += 1;
        this.lastIntent = intent;
        this.updatedAt = Instant.now();
        return this.turnCount;
    }

    public void requestContact() {
        this.status = CONTACT_REQUESTED;
        this.updatedAt = Instant.now();
    }

    public void activate() {
        this.status = ACTIVE;
        this.updatedAt = Instant.now();
    }

    public void recordCurrentSummary(String summary) {
        this.currentSummary = summary == null || summary.isBlank() ? null : summary.trim();
        this.updatedAt = Instant.now();
    }

    public void close(String status) {
        this.status = status;
        this.closedAt = Instant.now();
        this.updatedAt = this.closedAt;
    }

    public boolean isClosed() {
        return COMPLETED.equals(status) || SERVICE_REDIRECTED.equals(status);
    }
}
