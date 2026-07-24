package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_follow_subscription")
public class CustomerFollowSubscriptionEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "company_id", nullable = false, length = 64)
    private String companyId;
    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;
    @Column(name = "crm_account_id", nullable = false, length = 128)
    private String crmAccountId;
    @Column(name = "notification_policy", nullable = false, length = 64)
    private String notificationPolicy;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerFollowSubscriptionEntity() {}

    public CustomerFollowSubscriptionEntity(String companyId, String userId, String crmAccountId, String notificationPolicy) {
        this.companyId = companyId;
        this.userId = userId;
        this.crmAccountId = crmAccountId;
        this.notificationPolicy = notificationPolicy;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    public String getCrmAccountId() { return crmAccountId; }
    public String getNotificationPolicy() { return notificationPolicy; }
}
