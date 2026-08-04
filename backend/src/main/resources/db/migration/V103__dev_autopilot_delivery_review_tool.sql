-- Explicitly bind the product-manager review capability to existing DEV Autopilot PM agents.
INSERT INTO agent_tool_binding (
    company_id, agent_id, tool_id, priority, enabled, created_at
)
SELECT company_id, agent_id, 'semattice_project_delivery_review', 30, TRUE, CURRENT_TIMESTAMP
FROM agent_definition
WHERE agent_id = 'dev-autopilot-pm'
ON CONFLICT (company_id, agent_id, tool_id) DO UPDATE
SET enabled = TRUE,
    priority = EXCLUDED.priority;
