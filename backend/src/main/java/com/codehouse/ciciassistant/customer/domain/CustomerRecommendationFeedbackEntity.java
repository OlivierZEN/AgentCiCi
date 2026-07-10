package com.codehouse.ciciassistant.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "customer_recommendation_feedback")
public class CustomerRecommendationFeedbackEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false, length = 64)
    private String orgId;

    @Column(name = "user_id", nullable = false, length = 64)
    private String userId;

    @Column(name = "recommendation_id", nullable = false, length = 64)
    private String recommendationId;

    @Column(name = "rating", nullable = false, length = 32)
    private String rating;

    @Column(name = "comment_text", columnDefinition = "TEXT")
    private String commentText;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected CustomerRecommendationFeedbackEntity() {}

    public CustomerRecommendationFeedbackEntity(String orgId, String userId, String recommendationId,
                                                 String rating, String commentText) {
        this.orgId = orgId;
        this.userId = userId;
        this.recommendationId = recommendationId;
        update(rating, commentText);
        this.createdAt = this.updatedAt;
    }

    public void update(String rating, String commentText) {
        this.rating = rating;
        this.commentText = commentText;
        this.updatedAt = Instant.now();
    }

    public String getRating() { return rating; }
    public String getCommentText() { return commentText; }
    public Instant getUpdatedAt() { return updatedAt; }
}
