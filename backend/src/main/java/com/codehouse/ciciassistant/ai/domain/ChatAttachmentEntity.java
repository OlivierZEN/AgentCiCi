package com.codehouse.ciciassistant.ai.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "chat_attachment")
public class ChatAttachmentEntity {

    public static final String STATUS_READY = "READY";
    public static final String STATUS_ATTACHED = "ATTACHED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, length = 64, unique = true)
    private String publicId;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "slot_no", nullable = false)
    private int slotNo;

    @Column(name = "client_attachment_id", nullable = false, length = 96)
    private String clientAttachmentId;

    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "original_name", nullable = false, length = 255)
    private String originalName;

    @Column(name = "content_type", nullable = false, length = 64)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "sha256", nullable = false, length = 64)
    private String sha256;

    @Column(name = "storage_path", nullable = false, columnDefinition = "TEXT")
    private String storagePath;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected ChatAttachmentEntity() {
    }

    public ChatAttachmentEntity(String publicId, String companyId, String userId, String sessionId,
                                int slotNo, String clientAttachmentId, String originalName,
                                String contentType, long sizeBytes, String sha256, String storagePath) {
        this.publicId = publicId;
        this.companyId = companyId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.slotNo = slotNo;
        this.clientAttachmentId = clientAttachmentId;
        this.originalName = originalName;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.sha256 = sha256;
        this.storagePath = storagePath;
        this.status = STATUS_READY;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public void attachTo(Long targetMessageId) {
        if (!STATUS_READY.equals(status) || messageId != null) {
            throw new IllegalStateException("ATTACHMENT_NOT_READY");
        }
        messageId = targetMessageId;
        status = STATUS_ATTACHED;
        updatedAt = Instant.now();
    }

    public Long getId() { return id; }
    public String getPublicId() { return publicId; }
    public String getCompanyId() { return companyId; }
    public String getUserId() { return userId; }
    public String getSessionId() { return sessionId; }
    public int getSlotNo() { return slotNo; }
    public String getClientAttachmentId() { return clientAttachmentId; }
    public Long getMessageId() { return messageId; }
    public String getOriginalName() { return originalName; }
    public String getContentType() { return contentType; }
    public long getSizeBytes() { return sizeBytes; }
    public String getSha256() { return sha256; }
    public String getStoragePath() { return storagePath; }
    public String getStatus() { return status; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
}
