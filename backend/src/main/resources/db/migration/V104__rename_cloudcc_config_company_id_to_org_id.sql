-- CloudCC Token API uses orgId. integration_app.company_id remains the AgentCiCi tenant foreign key.
-- Preserve the legacy config key for a rolling deployment, but materialize the canonical orgId key.
WITH candidates AS (
    SELECT id,
           COALESCE(
               NULLIF(BTRIM(config_json::jsonb ->> 'companyId'), ''),
               NULLIF((regexp_match(config_json::jsonb ->> 'orgapi_switch_address', '[?&]orgId=([^&]+)'))[1], '')
           ) AS org_id
    FROM integration_app
    WHERE app_code = 'cloudcc_crm'
      AND NULLIF(BTRIM(config_json::jsonb ->> 'orgId'), '') IS NULL
)
UPDATE integration_app app
SET config_json = jsonb_set(app.config_json::jsonb, '{orgId}', to_jsonb(candidates.org_id), true)::text,
    updated_at = CURRENT_TIMESTAMP
FROM candidates
WHERE app.id = candidates.id
  AND candidates.org_id IS NOT NULL;
