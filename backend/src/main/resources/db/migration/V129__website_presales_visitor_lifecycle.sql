ALTER TABLE sisi_embed_session
    ADD COLUMN IF NOT EXISTS routing_key VARCHAR(160);

CREATE TABLE website_visitor_profile (
    id VARCHAR(64) PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    external_tenant_id VARCHAR(128) NOT NULL,
    external_user_id VARCHAR(128) NOT NULL,
    last_summary TEXT,
    has_lead BOOLEAN NOT NULL DEFAULT FALSE,
    last_visit_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_website_visitor_profile_company FOREIGN KEY (company_id) REFERENCES company(id),
    CONSTRAINT uk_website_visitor_profile_identity UNIQUE (
        company_id, agent_id, external_tenant_id, external_user_id
    )
);

CREATE INDEX idx_website_visitor_profile_company_updated
    ON website_visitor_profile(company_id, updated_at DESC);

CREATE TABLE website_visit_session (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    chat_session_id VARCHAR(64) NOT NULL,
    external_visit_id VARCHAR(64),
    status VARCHAR(32) NOT NULL,
    resume_choice VARCHAR(24),
    inherited_summary TEXT,
    current_summary TEXT,
    turn_count INTEGER NOT NULL DEFAULT 0,
    last_intent VARCHAR(32),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    closed_at TIMESTAMP,
    CONSTRAINT fk_website_visit_profile FOREIGN KEY (profile_id) REFERENCES website_visitor_profile(id),
    CONSTRAINT fk_website_visit_chat FOREIGN KEY (chat_session_id, company_id)
        REFERENCES chat_session(id, company_id) ON DELETE CASCADE,
    CONSTRAINT uk_website_visit_chat UNIQUE (chat_session_id),
    CONSTRAINT ck_website_visit_status CHECK (status IN (
        'AWAITING_CHOICE', 'ACTIVE', 'CONTACT_REQUESTED', 'COMPLETED', 'SERVICE_REDIRECTED'
    )),
    CONSTRAINT ck_website_visit_resume_choice CHECK (
        resume_choice IS NULL OR resume_choice IN ('CONTINUE', 'START_NEW')
    ),
    CONSTRAINT ck_website_visit_turn_count CHECK (turn_count >= 0)
);

CREATE INDEX idx_website_visit_profile_updated
    ON website_visit_session(profile_id, updated_at DESC);

CREATE TABLE website_presales_lead (
    id VARCHAR(64) PRIMARY KEY,
    profile_id VARCHAR(64) NOT NULL,
    company_id VARCHAR(64) NOT NULL,
    agent_id VARCHAR(64) NOT NULL,
    chat_session_id VARCHAR(64) NOT NULL,
    contact_type VARCHAR(16) NOT NULL,
    contact_cipher TEXT NOT NULL,
    contact_iv VARCHAR(64) NOT NULL,
    contact_hash VARCHAR(64) NOT NULL,
    need_summary TEXT,
    source VARCHAR(32) NOT NULL,
    consented_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL,
    CONSTRAINT fk_website_presales_lead_profile FOREIGN KEY (profile_id) REFERENCES website_visitor_profile(id),
    CONSTRAINT fk_website_presales_lead_chat FOREIGN KEY (chat_session_id, company_id)
        REFERENCES chat_session(id, company_id),
    CONSTRAINT ck_website_presales_lead_contact_type CHECK (contact_type IN ('MOBILE', 'EMAIL')),
    CONSTRAINT uk_website_presales_lead_contact UNIQUE (
        company_id, agent_id, profile_id, contact_hash
    )
);

CREATE INDEX idx_website_presales_lead_company_created
    ON website_presales_lead(company_id, created_at DESC);
