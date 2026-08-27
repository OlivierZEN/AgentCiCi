INSERT INTO internal_application(
    app_code, display_name, summary, icon_key, owner_team, tenant_mode, catalog_status,
    trusted_app_code, launch_mode, launch_route_key, default_version, created_by)
VALUES (
    'demo-example', 'DEMO示例应用', '单页单对象的应用中心完整配置参考', 'application',
    'AgentCiCi', 'PLATFORM_BASE', 'PUBLISHED', NULL, 'PLATFORM_ROUTE',
    'demo-example.page', '1.0.0', 'platform-seed')
ON CONFLICT (app_code) DO NOTHING;

INSERT INTO internal_application_version(
    id, app_code, version, manifest_schema_version, provider_binding_key,
    initialization_engine, manifest_json, manifest_digest, version_status,
    created_by, validated_by, validated_at, published_by, published_at)
VALUES (
    'catalog-demo-example-1-0-0', 'demo-example', '1.0.0', 'tenant-application/v1', NULL,
    'NONE',
    '{"schemaVersion":"tenant-application/v1","initializationEngine":"NONE","steps":[]}'::jsonb,
    '13d86225c2f8f980fa05d593bf1e30a6fea6ee903be70d2ef2d0d4af55ca6596',
    'PUBLISHED', 'platform-seed', 'platform-seed', CURRENT_TIMESTAMP,
    'platform-seed', CURRENT_TIMESTAMP)
ON CONFLICT (app_code, version) DO NOTHING;

INSERT INTO internal_application_dependency(
    id, application_version_id, dependency_app_code, version_constraint,
    dependency_type, activation_policy)
VALUES (
    'catalog-demo-example-dependency-semattice', 'catalog-demo-example-1-0-0',
    'semattice', '>=1.0.0', 'OPTIONAL', 'AUTO_PROVISION_ALLOWED')
ON CONFLICT (application_version_id, dependency_app_code) DO NOTHING;
