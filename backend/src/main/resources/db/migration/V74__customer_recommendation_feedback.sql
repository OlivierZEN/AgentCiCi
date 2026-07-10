CREATE TABLE IF NOT EXISTS customer_recommendation_feedback (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    user_id VARCHAR(64) NOT NULL,
    recommendation_id VARCHAR(64) NOT NULL,
    rating VARCHAR(32) NOT NULL,
    comment_text TEXT,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_customer_recommendation_feedback_org FOREIGN KEY (org_id) REFERENCES org(id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_customer_recommendation_feedback_user
    ON customer_recommendation_feedback(org_id, user_id, recommendation_id);
