package com.codehouse.ciciassistant.wecom.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "wecom_kf_message")
public class WecomKfMessageEntity {

    public static final String DIRECTION_INBOUND = "INBOUND";
    public static final String DIRECTION_OUTBOUND = "OUTBOUND";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;

    @Column(name = "msg_id", nullable = false, length = 128)
    private String msgId;

    @Column(name = "corp_id", nullable = false, length = 64)
    private String corpId;

    @Column(name = "open_kfid", nullable = false, length = 128)
    private String openKfId;

    @Column(name = "external_userid", length = 128)
    private String externalUserId;

    @Column(name = "direction", nullable = false, length = 16)
    private String direction;

    @Column(name = "msg_type", nullable = false, length = 32)
    private String msgType;

    @Column(name = "content_summary", columnDefinition = "TEXT")
    private String contentSummary;

    @Column(name = "trace_id", length = 64)
    private String traceId;

    @Column(name = "send_status", length = 32)
    private String sendStatus;

    @Column(name = "origin")
    private Integer origin;

    @Column(name = "servicer_userid", length = 128)
    private String servicerUserId;

    @Column(name = "event_type", length = 64)
    private String eventType;

    @Column(name = "remote_msg_id", length = 128)
    private String remoteMsgId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    protected WecomKfMessageEntity() {
    }

    public WecomKfMessageEntity(String companyId,
                                String msgId,
                                String corpId,
                                String openKfId,
                                String externalUserId,
                                String direction,
                                String msgType,
                                String contentSummary,
                                String traceId,
                                String sendStatus) {
        this(companyId, msgId, corpId, openKfId, externalUserId, direction, msgType, contentSummary,
                traceId, sendStatus, null, null, null, null);
    }

    public WecomKfMessageEntity(String companyId,
                                String msgId,
                                String corpId,
                                String openKfId,
                                String externalUserId,
                                String direction,
                                String msgType,
                                String contentSummary,
                                String traceId,
                                String sendStatus,
                                Integer origin,
                                String servicerUserId,
                                String eventType,
                                String remoteMsgId) {
        this.companyId = companyId;
        this.msgId = msgId;
        this.corpId = corpId;
        this.openKfId = openKfId;
        this.externalUserId = externalUserId;
        this.direction = direction;
        this.msgType = msgType;
        this.contentSummary = contentSummary;
        this.traceId = traceId;
        this.sendStatus = sendStatus;
        this.origin = origin;
        this.servicerUserId = servicerUserId;
        this.eventType = eventType;
        this.remoteMsgId = remoteMsgId;
    }

    public Long getId() { return id; }
    public String getCompanyId() { return companyId; }
    public String getMsgId() { return msgId; }
    public String getCorpId() { return corpId; }
    public String getOpenKfId() { return openKfId; }
    public String getExternalUserId() { return externalUserId; }
    public String getDirection() { return direction; }
    public String getMsgType() { return msgType; }
    public String getContentSummary() { return contentSummary; }
    public String getTraceId() { return traceId; }
    public String getSendStatus() { return sendStatus; }
    public Integer getOrigin() { return origin; }
    public String getServicerUserId() { return servicerUserId; }
    public String getEventType() { return eventType; }
    public String getRemoteMsgId() { return remoteMsgId; }
    public Instant getCreatedAt() { return createdAt; }
}
