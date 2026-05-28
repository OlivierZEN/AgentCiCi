CREATE TABLE IF NOT EXISTS billing_edition_config (
    id BIGSERIAL PRIMARY KEY,
    org_id VARCHAR(64) NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    item_code VARCHAR(64) NOT NULL,
    display_name VARCHAR(128) NOT NULL,
    deployment_mode VARCHAR(32) NOT NULL,
    version_no INTEGER NOT NULL,
    publish_status VARCHAR(32) NOT NULL,
    enabled BOOLEAN NOT NULL,
    billing_type_policy VARCHAR(32) NOT NULL,
    included_credits INTEGER NOT NULL DEFAULT 0,
    operation_seat_limit INTEGER,
    builder_seat_limit INTEGER,
    agent_limit INTEGER,
    skill_workflow_limit INTEGER,
    knowledge_capacity_gb INTEGER,
    open_api_qps INTEGER,
    open_api_concurrency INTEGER,
    open_api_credential_limit INTEGER,
    connector_limit INTEGER,
    meeting_concurrency INTEGER,
    trace_retention_days INTEGER,
    audit_retention_days INTEGER,
    environment_limit INTEGER,
    overage_mode VARCHAR(32) NOT NULL,
    sla_tier_code VARCHAR(64),
    addon_category VARCHAR(64),
    pricing_unit VARCHAR(64),
    policy_json TEXT,
    change_reason VARCHAR(1000) NOT NULL,
    created_by VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_billing_edition_config_org_type_code_version
    ON billing_edition_config(org_id, item_type, item_code, version_no);

CREATE INDEX IF NOT EXISTS idx_billing_edition_config_org_status
    ON billing_edition_config(org_id, publish_status, item_type, item_code);

CREATE INDEX IF NOT EXISTS idx_billing_edition_config_org_type_code
    ON billing_edition_config(org_id, item_type, item_code, version_no DESC);

INSERT INTO billing_edition_config (
    org_id, item_type, item_code, display_name, deployment_mode, version_no,
    publish_status, enabled, billing_type_policy, included_credits,
    operation_seat_limit, builder_seat_limit, agent_limit, skill_workflow_limit,
    knowledge_capacity_gb, open_api_qps, open_api_concurrency, open_api_credential_limit,
    connector_limit, meeting_concurrency, trace_retention_days, audit_retention_days,
    environment_limit, overage_mode, sla_tier_code, addon_category, pricing_unit,
    policy_json, change_reason, created_by, created_at, updated_at, published_at
)
VALUES
    ('demo-org', 'PLAN', 'saas_team', '团队版', 'saas', 1, 'PUBLISHED', TRUE, 'platform_paid', 20000,
     10, 3, 8, 30, 20, 20, 5, 3, 8, 1, 30, 180, 1, 'soft_limit', 'standard', NULL, 'org_month',
     '{"creditsPolicy":"included_then_top_up","platformPaidResources":true}', 'seed SaaS team edition', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'PLAN', 'saas_business', '商业版', 'saas', 1, 'PUBLISHED', TRUE, 'platform_paid', 100000,
     80, 15, 40, 160, 200, 100, 25, 12, 40, 4, 90, 365, 2, 'auto_charge', 'business', NULL, 'org_month',
     '{"creditsPolicy":"included_top_up_and_contract_overage","platformPaidResources":true}', 'seed SaaS business edition', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'PLAN', 'saas_enterprise', '企业版', 'saas', 1, 'PUBLISHED', TRUE, 'platform_paid', 500000,
     NULL, NULL, NULL, NULL, NULL, 500, 100, 50, NULL, 12, 365, 1095, 4, 'auto_charge', 'enterprise', NULL, 'org_year',
     '{"creditsPolicy":"contract_allowance","platformPaidResources":true,"sso":true}', 'seed SaaS enterprise edition', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'PLAN', 'private_department', '部门版', 'private_deployment', 1, 'PUBLISHED', TRUE, 'customer_paid', 0,
     30, 8, 20, 80, 100, 50, 15, 6, 20, 2, 90, 365, 1, 'soft_limit', 'standard', NULL, 'license_year',
     '{"localModelTokenDoubleCharge":false,"creditsPolicy":"observability_and_budget"}', 'seed private department edition', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'PLAN', 'private_enterprise', '企业版', 'private_deployment', 1, 'PUBLISHED', TRUE, 'customer_paid', 0,
     200, 50, 120, 500, 1000, 200, 50, 20, 100, 8, 180, 1095, 3, 'soft_limit', 'business', NULL, 'license_year',
     '{"localModelTokenDoubleCharge":false,"creditsPolicy":"contract_allowance_for_platform_paid_resources"}', 'seed private enterprise edition', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'PLAN', 'private_group', '集团版', 'private_deployment', 1, 'PUBLISHED', TRUE, 'customer_paid', 0,
     NULL, NULL, NULL, NULL, NULL, 500, 120, 50, NULL, 20, 365, 1825, 8, 'auto_charge', 'enterprise', NULL, 'license_year',
     '{"localModelTokenDoubleCharge":false,"multiInstance":true,"drEnvironment":true}', 'seed private group edition', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'CAPACITY_PACK', 'capacity_agents_50', '智能体容量包 50', 'all', 1, 'PUBLISHED', TRUE, 'included', 0,
     NULL, NULL, 50, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'soft_limit', NULL, 'agent_capacity', 'pack_year',
     '{"increment":{"agentLimit":50}}', 'seed agent capacity pack', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'MODULE_PACK', 'module_open_api', 'Open API 生产模块包', 'all', 1, 'PUBLISHED', TRUE, 'platform_paid', 0,
     NULL, NULL, NULL, NULL, NULL, 100, 20, 10, NULL, NULL, 90, 365, NULL, 'auto_charge', NULL, 'open_api', 'module_year',
     '{"features":["open_api_production_access","credential_governance"]}', 'seed Open API module pack', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'SERVICE_PACK', 'service_implementation', '实施运维服务包', 'private_deployment', 1, 'PUBLISHED', TRUE, 'non_billable', 0,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'soft_limit', 'business', 'implementation', 'service_project',
     '{"serviceItems":["implementation","training","quarterly_review"]}', 'seed implementation service pack', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'SLA_TIER', 'sla_enterprise', '企业 SLA', 'all', 1, 'PUBLISHED', TRUE, 'non_billable', 0,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 365, 1095, NULL, 'soft_limit', 'enterprise', 'sla', 'tier_year',
     '{"responseHours":{"p1":4,"p2":8},"supportChannel":"dedicated"}', 'seed enterprise SLA tier', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'CREDITS_POLICY', 'credits_saas_default', 'SaaS Credits 策略', 'saas', 1, 'PUBLISHED', TRUE, 'platform_paid', 0,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'auto_charge', NULL, 'credits', 'policy',
     '{"topUpEnabled":true,"rollover":"none","platformPaidResources":true}', 'seed SaaS credits policy', 'platform-system', NOW(), NOW(), NOW()),
    ('demo-org', 'CREDITS_POLICY', 'credits_private_default', '私有化 Credits 策略', 'private_deployment', 1, 'PUBLISHED', TRUE, 'customer_paid', 0,
     NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL, 'soft_limit', NULL, 'credits', 'policy',
     '{"topUpEnabled":false,"localModelTokenDoubleCharge":false,"purpose":"observability_budget_and_platform_paid_resources"}', 'seed private credits policy', 'platform-system', NOW(), NOW(), NOW())
ON CONFLICT (org_id, item_type, item_code, version_no) DO NOTHING;
