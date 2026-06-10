CREATE INDEX IF NOT EXISTS idx_audit_log_org_created
    ON audit_log (org_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_audit_log_org_event_created
    ON audit_log (org_id, event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_audit_log_org_event_created
    ON platform_audit_log (org_id, event_type, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_platform_audit_log_org_resource_created
    ON platform_audit_log (org_id, resource_type, created_at DESC);
