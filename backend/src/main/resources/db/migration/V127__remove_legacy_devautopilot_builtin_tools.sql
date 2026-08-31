-- INT-029: DevAutopilot delivery capabilities are application-bound MCP tools.
-- Remove the stale platform-native catalog rows; agent tool bindings keep the same stable names
-- and are resolved through tenant_application_mcp_binding at authoring and runtime.
DELETE FROM platform_tool_definition
WHERE tool_name IN (
    'semattice_project_delivery_query',
    'semattice_project_delivery_create',
    'semattice_project_delivery_update',
    'semattice_project_delivery_transfer',
    'semattice_project_delivery_delete',
    'semattice_project_delivery_review'
);
